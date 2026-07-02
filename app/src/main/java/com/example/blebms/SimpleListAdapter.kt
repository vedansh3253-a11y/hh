package com.example.blebms

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Generic one-line list adapter used for both the scanned-device list
 * and the discovered-characteristic list.
 */
class SimpleListAdapter(
    private val items: MutableList<String> = mutableListOf(),
    private val onClick: (Int) -> Unit,
    private val onLongClick: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<SimpleListAdapter.RowHolder>() {

    class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.rowText)
    }

    fun setItems(newItems: List<String>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun addOrUpdate(label: String) {
        val existingIndex = items.indexOfFirst { it == label }
        if (existingIndex == -1) {
            items.add(label)
            notifyItemInserted(items.size - 1)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_row, parent, false)
        return RowHolder(view)
    }

    override fun onBindViewHolder(holder: RowHolder, position: Int) {
        holder.text.text = items[position]
        holder.itemView.setOnClickListener { onClick(position) }
        holder.itemView.setOnLongClickListener {
            onLongClick?.invoke(position)
            true
        }
    }

    override fun getItemCount(): Int = items.size
}
