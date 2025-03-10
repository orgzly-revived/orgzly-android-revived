package com.orgzly.android.ui.settings.exporting

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.orgzly.R
import com.orgzly.android.db.entity.Book
import com.orgzly.android.db.entity.Note
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.ui.notes.NoteItemViewBinder
import com.orgzly.databinding.ItemSettingsExportBinding

class SettingsExportAdapter(val context: Context, val listener: OnClickListener) :
    ListAdapter<SettingsExportViewModel.Item, SettingsExportAdapter.SettingsExportViewHolder>(
        DIFF_CALLBACK
    ) {

    data class Icons(@DrawableRes val up: Int, @DrawableRes val book: Int)

    private var icons: Icons? = null

    private val noteItemViewBinder = NoteItemViewBinder(context, true)

    interface OnClickListener {
        fun onItem(item: SettingsExportViewModel.Item)
        fun onButton(item: SettingsExportViewModel.Item)
    }

    class SettingsExportViewHolder(val binding: ItemSettingsExportBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingsExportViewHolder {
        val holder = SettingsExportViewHolder(ItemSettingsExportBinding.inflate(
            LayoutInflater.from(parent.context), parent, false))

        holder.binding.itemSettingsExportPayload.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onItem(getItem(holder.bindingAdapterPosition))
            }
        }

        holder.binding.itemSettingsExportButton.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onButton(getItem(holder.bindingAdapterPosition))
            }
        }

        return holder
    }

    override fun onBindViewHolder(holder: SettingsExportViewHolder, position: Int) {

        if (icons == null) {
            icons = Icons(R.drawable.ic_keyboard_arrow_up, R.drawable.ic_library_books)
        }

        val item = getItem(position)

        when (val payload = item.payload) {
            is Book -> {
                holder.binding.itemSettingsExportName.text = payload.title ?: payload.name

                holder.binding.itemSettingsExportButton.visibility = View.GONE

                holder.binding.itemSettingsExportIcon.visibility = View.VISIBLE
            }

            is Note -> {
                holder.binding.itemSettingsExportName.text = noteItemViewBinder.generateTitle(
                    NoteView(note = payload, bookName = ""))
                icons?.let {
                    if (payload.position.descendantsCount > 0) {
                        holder.binding.itemSettingsExportIcon.setImageResource(R.drawable.bullet_folded)
                        holder.binding.itemSettingsExportButton.visibility = View.GONE
                    } else {
                        holder.binding.itemSettingsExportIcon.setImageResource(R.drawable.bullet)
                        holder.binding.itemSettingsExportButton.visibility = View.VISIBLE
                    }
                    holder.binding.itemSettingsExportIcon.visibility = View.VISIBLE
                }
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<SettingsExportViewModel.Item> =
            object : DiffUtil.ItemCallback<SettingsExportViewModel.Item>() {
                override fun areItemsTheSame(oldItem: SettingsExportViewModel.Item, newItem: SettingsExportViewModel.Item): Boolean {
                    return oldItem == newItem
                }

                override fun areContentsTheSame(oldItem: SettingsExportViewModel.Item, newItem: SettingsExportViewModel.Item): Boolean {
                    return oldItem == newItem
                }
            }
    }
}