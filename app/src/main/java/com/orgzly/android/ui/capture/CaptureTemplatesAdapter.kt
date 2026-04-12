package com.orgzly.android.ui.capture

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.orgzly.R
import com.orgzly.databinding.ItemCaptureTemplateBinding

class CaptureTemplatesAdapter(
    private val onItemClick: (CaptureTemplate) -> Unit,
    private val onItemLongClick: (CaptureTemplate) -> Boolean
) : ListAdapter<CaptureTemplate, CaptureTemplatesAdapter.ViewHolder>(DIFF_CALLBACK) {

    inner class ViewHolder(val binding: ItemCaptureTemplateBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    try { onItemClick(getItem(position)) } catch (_: IndexOutOfBoundsException) {}
                }
            }
            binding.root.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    try { onItemLongClick(getItem(position)) } catch (_: IndexOutOfBoundsException) { false }
                } else {
                    false
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemCaptureTemplateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val template = getItem(position)
        holder.binding.description.text = template.getDisplayName(
            holder.itemView.context.getString(R.string.capture_template_numbered, position + 1)
        )
        val details = buildString {
            if (template.targetBook.isNotBlank()) append(template.targetBook)
            if (template.state.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(template.state)
            }
            if (template.tags.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(template.tags)
            }
        }
        holder.binding.details.text = details
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<CaptureTemplate>() {
            override fun areItemsTheSame(oldItem: CaptureTemplate, newItem: CaptureTemplate) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: CaptureTemplate, newItem: CaptureTemplate) =
                oldItem == newItem
        }
    }
}
