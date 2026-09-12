package com.legtracking.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.legtracking.data.TrackEntity
import com.legtracking.databinding.ItemTrackBinding
import java.text.SimpleDateFormat
import java.util.Locale

class TrackAdapter(
    private val onTrackClick: (TrackEntity) -> Unit
) : RecyclerView.Adapter<TrackAdapter.TrackViewHolder>() {

    private val items = mutableListOf<TrackEntity>()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))

    fun submitList(newItems: List<TrackEntity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val binding = ItemTrackBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TrackViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class TrackViewHolder(
        private val binding: ItemTrackBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(track: TrackEntity) {
            binding.textTrackName.text = track.name

            val distanceKm = track.distanceMeters / 1000.0
            val durationMinutes = if (track.endedAt != null) {
                (track.endedAt - track.startedAt) / 60000
            } else {
                0
            }

            binding.textTrackMeta.text = buildString {
                append(dateFormat.format(track.startedAt))
                append(" • ")
                append(String.format(Locale("id", "ID"), "%.1f km", distanceKm))
                if (durationMinutes > 0) {
                    append(" • ")
                    append("$durationMinutes menit")
                }
            }

            binding.root.setOnClickListener { onTrackClick(track) }
        }
    }
}
