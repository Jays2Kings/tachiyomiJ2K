package eu.kanade.tachiyomi.util.filter

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

interface Serializer<in T : Filter<out Any?>> {
    fun JsonObjectBuilder.serialize(filter: T) {}

    fun deserialize(
        json: JsonObject,
        filter: T,
    ) {}

    /**
     * Automatic two-way mappings between fields and JSON
     */
    fun mappings(): List<Pair<String, KProperty1<in T, *>>> = emptyList()

    val serializer: FilterSerializer
    val type: String
    val clazz: KClass<in T>
}

class HeaderSerializer(
    override val serializer: FilterSerializer,
) : Serializer<Filter.Header> {
    override val type = "HEADER"
    override val clazz = Filter.Header::class

    override fun mappings() =
        listOf(
            Pair(NAME, Filter.Header::name),
        )

    companion object {
        const val NAME = "name"
    }
}

class SeparatorSerializer(
    override val serializer: FilterSerializer,
) : Serializer<Filter.Separator> {
    override val type = "SEPARATOR"
    override val clazz = Filter.Separator::class

    override fun mappings() =
        listOf(
            Pair(NAME, Filter.Separator::name),
        )

    companion object {
        const val NAME = "name"
    }
}

class SelectSerializer(
    override val serializer: FilterSerializer,
) : Serializer<Filter.Select<Any>> {
    override val type = "SELECT"
    override val clazz = Filter.Select::class

    override fun JsonObjectBuilder.serialize(filter: Filter.Select<Any>) {
        // Serialize values to JSON
        putJsonArray(VALUES) {
            filter.values
                .map {
                    it.toString()
                }.forEach { add(it) }
        }
    }

    override fun mappings() =
        listOf(
            Pair(NAME, Filter.Select<Any>::name),
            Pair(STATE, Filter.Select<Any>::state),
        )

    companion object {
        const val NAME = "name"
        const val VALUES = "values"
        const val STATE = "state"
    }
}

class TextSerializer(
    override val serializer: FilterSerializer,
) : Serializer<Filter.Text> {
    override val type = "TEXT"
    override val clazz = Filter.Text::class

    override fun mappings() =
        listOf(
            Pair(NAME, Filter.Text::name),
            Pair(STATE, Filter.Text::state),
        )

    companion object {
        const val NAME = "name"
        const val STATE = "state"
    }
}

class CheckboxSerializer(
    override val serializer: FilterSerializer,
) : Serializer<Filter.CheckBox> {
    override val type = "CHECKBOX"
    override val clazz = Filter.CheckBox::class

    override fun mappings() =
        listOf(
            Pair(NAME, Filter.CheckBox::name),
            Pair(STATE, Filter.CheckBox::state),
        )

    companion object {
        const val NAME = "name"
        const val STATE = "state"
    }
}

class TriStateSerializer(
    override val serializer: FilterSerializer,
) : Serializer<Filter.TriState> {
    override val type = "TRISTATE"
    override val clazz = Filter.TriState::class

    override fun mappings() =
        listOf(
            Pair(NAME, Filter.TriState::name),
            Pair(STATE, Filter.TriState::state),
        )

    companion object {
        const val NAME = "name"
        const val STATE = "state"
    }
}

class GroupSerializer(
    override val serializer: FilterSerializer,
) : Serializer<Filter.Group<Any?>> {
    override val type = "GROUP"
    override val clazz = Filter.Group::class

    override fun JsonObjectBuilder.serialize(filter: Filter.Group<Any?>) {
        putJsonArray(STATE) {
            filter.state.forEach {
                add(
                    if (it is Filter<*>) {
                        @Suppress("UNCHECKED_CAST")
                        serializer.serialize(it as Filter<Any?>)
                    } else {
                        JsonNull as JsonElement
                    },
                )
            }
        }
    }

    override fun deserialize(
        json: JsonObject,
        filter: Filter.Group<Any?>,
    ) {
        @Suppress("UNCHECKED_CAST")
        val subFilters = filter.state.filterIsInstance<Filter<Any?>>()
        val state = json[STATE]?.jsonArray ?: return
        serializer.deserializeMatchingByName(subFilters, state, parentName = filter.name)
    }

    override fun mappings() =
        listOf(
            Pair(NAME, Filter.Group<Any?>::name),
        )

    companion object {
        const val NAME = "name"
        const val STATE = "state"
    }
}

class SortSerializer(
    override val serializer: FilterSerializer,
) : Serializer<Filter.Sort> {
    override val type = "SORT"
    override val clazz = Filter.Sort::class

    override fun JsonObjectBuilder.serialize(filter: Filter.Sort) {
        // Serialize values
        putJsonArray(VALUES) {
            filter.values.forEach { add(it) }
        }
        // Serialize state
        put(
            STATE,
            filter.state?.let { (index, ascending) ->
                buildJsonObject {
                    put(STATE_INDEX, index)
                    put(STATE_ASCENDING, ascending)
                }
            } ?: (JsonNull as JsonElement),
        )
    }

    override fun deserialize(
        json: JsonObject,
        filter: Filter.Sort,
    ) {
        // Deserialize state
        filter.state =
            (json[STATE] as? JsonObject)?.let {
                val index = it[STATE_INDEX]?.jsonPrimitive?.int ?: return@let null
                val ascending = it[STATE_ASCENDING]?.jsonPrimitive?.boolean ?: return@let null
                Filter.Sort.Selection(index, ascending)
            }
    }

    override fun mappings() =
        listOf(
            Pair(NAME, Filter.Sort::name),
        )

    companion object {
        const val NAME = "name"
        const val VALUES = "values"
        const val STATE = "state"

        const val STATE_INDEX = "index"
        const val STATE_ASCENDING = "ascending"
    }
}
