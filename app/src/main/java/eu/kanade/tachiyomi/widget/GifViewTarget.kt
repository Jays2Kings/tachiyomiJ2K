package eu.kanade.tachiyomi.widget

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import coil.target.ImageViewTarget
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.visible

class GifViewTarget(view: ImageView, private val progressBar: View?, private val decodeErrorLayout: ViewGroup?) : ImageViewTarget(view) {

    override fun onError(error: Drawable?) {
        progressBar?.gone()
        decodeErrorLayout?.visible()
    }

    override fun onSuccess(result: Drawable) {
        progressBar?.gone()
        decodeErrorLayout?.gone()
        super.onSuccess(result)
    }
}
