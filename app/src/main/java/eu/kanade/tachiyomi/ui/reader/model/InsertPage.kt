package eu.kanade.tachiyomi.ui.reader.model

class InsertPage(parent: ReaderPage) : ReaderPage(
    parent.index,
    parent.url,
    parent.imageUrl
) {

    override var chapter: ReaderChapter = parent.chapter

    init {
        fullPage = true
        firstHalf = false
        stream = parent.stream
    }
}
