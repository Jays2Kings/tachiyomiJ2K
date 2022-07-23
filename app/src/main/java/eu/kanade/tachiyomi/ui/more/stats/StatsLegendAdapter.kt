package eu.kanade.tachiyomi.ui.more.stats

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ListChartLegendBinding

class StatsLegendAdapter(
    private val list: List<StatsController.StatusDistributionItem>,
) : RecyclerView.Adapter<StatsLegendAdapter.StatsLegendHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatsLegendHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_chart_legend, parent, false)
        return StatsLegendHolder(view)
    }

    override fun onBindViewHolder(holder: StatsLegendHolder, position: Int) {
        val item = list[position]
        holder.legendColorIcon.imageTintList = ColorStateList.valueOf(item.color)
        holder.legendDescriptionText.text = item.status
        holder.legendValueText.text = item.amount.toString()
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class StatsLegendHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val binding = ListChartLegendBinding.bind(view)

        val legendColorIcon = binding.legendColorIcon
        val legendDescriptionText = binding.legendDescriptionText
        val legendValueText = binding.legendValueText
    }
}
