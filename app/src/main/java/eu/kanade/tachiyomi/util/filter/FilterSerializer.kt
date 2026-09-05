package eu.kanade.tachiyomi.util.filter

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import timber.log.Timber
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.isSubclassOf

class FilterSerializer {
    private val serializers =
        listOf<Serializer<*>>(
            HeaderSerializer(this),
            SeparatorSerializer(this),
            SelectSerializer(this),
            TextSerializer(this),
            CheckboxSerializer(this),
            TriStateSerializer(this),
            GroupSerializer(this),
            SortSerializer(this),
        )

    private val _skippedFilterNames = mutableListOf<String>()

    /**
     * Names of saved filters that couldn't be matched to a live filter, or that failed to
     * restore, during the most recent [deserialize] call — for example because the source's
     * extension added, removed, renamed, or reordered a filter since the search was saved.
     * Cleared at the start of every [deserialize] call, so read it right after calling that.
     */
    val skippedFilterNames: List<String>
        get() = _skippedFilterNames.distinct()

    fun serialize(filters: FilterList) =
        buildJsonArray {
            filters.filterIsInstance<Filter<Any?>>().forEach {
                add(serialize(it))
            }
        }

    fun serialize(filter: Filter<Any?>): JsonObject =
        serializerFor(filter)?.let { serializer ->
            buildJsonObject {
                with(serializer) { serialize(filter) }

                val classMappings = mutableListOf<Pair<String, Any>>()

                serializer.mappings().forEach {
                    val res = it.second.get(filter)
                    put(it.first, res.toString())
                    classMappings += it.first to (res?.javaClass?.name ?: "null")
                }

                putJsonObject(CLASS_MAPPINGS) {
                    classMappings.forEach { (t, u) ->
                        put(t, u.toString())
                    }
                }

                put(TYPE, serializer.type)
            }
        } ?: throw IllegalArgumentException("Cannot serialize this Filter object!")

    fun deserialize(
        filters: FilterList,
        json: JsonArray,
    ) {
        _skippedFilterNames.clear()
        deserializeMatchingByName(filters.filterIsInstance<Filter<Any?>>(), json)
    }

    fun deserialize(
        filter: Filter<Any?>,
        json: JsonObject,
    ) {
        val type = json[TYPE]?.jsonPrimitive?.contentOrNull ?: return
        val serializer =
            serializers
                .filterIsInstance<Serializer<Filter<Any?>>>()
                .firstOrNull {
                    it.type == type
                } ?: throw IllegalArgumentException("Cannot deserialize this type!")

        serializer.deserialize(json, filter)

        val classMappings = json[CLASS_MAPPINGS]?.jsonObject ?: return

        serializer.mappings().forEach { (key, property) ->
            if (property is KMutableProperty1) {
                val obj = json[key]?.jsonPrimitive ?: return@forEach
                val className = classMappings[key]?.jsonPrimitive?.contentOrNull ?: return@forEach
                val res: Any? =
                    when (className) {
                        java.lang.Integer::class.java.name -> obj.int
                        java.lang.Long::class.java.name -> obj.long
                        java.lang.Float::class.java.name -> obj.float
                        java.lang.Double::class.java.name -> obj.double
                        java.lang.String::class.java.name -> obj.content
                        java.lang.Boolean::class.java.name -> obj.boolean
                        java.lang.Byte::class.java.name -> obj.content.toByte()
                        java.lang.Short::class.java.name -> obj.content.toShort()
                        java.lang.Character::class.java.name -> obj.content[0]
                        "null" -> null
                        else -> throw IllegalArgumentException("Cannot deserialize this type!")
                    }
                @Suppress("UNCHECKED_CAST")
                (property as KMutableProperty1<in Filter<Any?>, in Any?>).set(filter, res)
            }
        }
    }

    /**
     * Matches each saved filter entry in [json] to a filter in [liveFilters] by name and type,
     * then restores its saved value onto that filter — instead of pairing them up by list
     * position.
     *
     * Position-based matching breaks silently if a source's filter list changes between when a
     * search was saved and when it's re-applied, e.g. an extension update that adds, removes, or
     * reorders a filter. A saved filter with no matching name+type is simply left at its default
     * rather than having its value applied to the wrong filter, and its name is added to
     * [skippedFilterNames] so the caller can let the user know. If more than one live filter
     * shares the same name and type, they're paired up in the order they appear. [parentName]
     * labels filters nested inside a group, e.g. "Genre: Fantasy".
     */
    internal fun deserializeMatchingByName(
        liveFilters: List<Filter<Any?>>,
        json: JsonArray,
        parentName: String? = null,
    ) {
        val usedIndices = mutableSetOf<Int>()

        json.forEach { element ->
            val obj = element as? JsonObject ?: return@forEach
            var label = "?"
            try {
                val savedName = obj[NAME]?.jsonPrimitive?.contentOrNull
                val savedType = obj[TYPE]?.jsonPrimitive?.contentOrNull
                label = if (parentName != null) "$parentName: ${savedName ?: "?"}" else savedName ?: "?"

                val matchIndex =
                    liveFilters.indices.firstOrNull { index ->
                        index !in usedIndices &&
                            liveFilters[index].name == savedName &&
                            serializerFor(liveFilters[index])?.type == savedType
                    }

                if (matchIndex == null) {
                    _skippedFilterNames += label
                    Timber.w("No filter matches saved filter \"$savedName\" ($savedType); skipping")
                    return@forEach
                }

                usedIndices += matchIndex
                deserialize(liveFilters[matchIndex], obj)
            } catch (e: Exception) {
                _skippedFilterNames += label
                Timber.e(e)
            }
        }
    }

    private fun serializerFor(filter: Filter<Any?>): Serializer<Filter<Any?>>? =
        serializers
            .filterIsInstance<Serializer<Filter<Any?>>>()
            .firstOrNull { filter::class.isSubclassOf(it.clazz) }

    companion object {
        const val NAME = "name"
        const val TYPE = "_type"
        const val CLASS_MAPPINGS = "_cmaps"
    }
}
