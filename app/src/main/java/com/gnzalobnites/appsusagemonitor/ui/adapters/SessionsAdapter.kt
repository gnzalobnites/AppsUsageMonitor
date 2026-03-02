package com.gnzalobnites.appsusagemonitor.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gnzalobnites.appsusagemonitor.R
import com.gnzalobnites.appsusagemonitor.data.entities.UsageSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionsAdapter : RecyclerView.Adapter<SessionsAdapter.SessionViewHolder>() {

    private var sessions = listOf<UsageSession>()

    fun submitList(list: List<UsageSession>) {
        sessions = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session, parent, false)
        return SessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val session = sessions[position]
        holder.bind(session)
    }

    override fun getItemCount(): Int = sessions.size

    inner class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val sessionStart: TextView = itemView.findViewById(R.id.session_start)
        private val sessionDuration: TextView = itemView.findViewById(R.id.session_duration)
        private val context = itemView.context

        fun bind(session: UsageSession) {
            val dateFormat = SimpleDateFormat(context.getString(R.string.time_format_hh_mm), Locale.getDefault())
            sessionStart.text = dateFormat.format(Date(session.startTime))
            
            val duration = if (session.endTime != null) session.duration else 
                System.currentTimeMillis() - session.startTime
            sessionDuration.text = formatDuration(duration)
        }

        private fun formatDuration(duration: Long): String {
            val seconds = (duration / 1000) % 60
            val minutes = (duration / (1000 * 60)) % 60
            val hours = (duration / (1000 * 60 * 60))
            
            return if (hours > 0) {
                String.format(context.getString(R.string.session_time_hours_format), hours, minutes, seconds)
            } else {
                String.format(context.getString(R.string.session_time_format), minutes, seconds)
            }
        }
    }
}
