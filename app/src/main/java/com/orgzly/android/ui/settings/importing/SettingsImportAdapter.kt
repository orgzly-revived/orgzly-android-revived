package com.orgzly.android.ui.settings.importing

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
import com.orgzly.databinding.ItemSettingsImportBinding

class SettingsImportAdapter(val context: Context, val listener: OnClickListener) :
    ListAdapter<SettingsImportViewModel.Item, SettingsImportAdapter.SettingsImportViewHolder>(
        DIFF_CALLBACK
    ) {

    data class Icons(@DrawableRes val up: Int, @DrawableRes val book: Int)

    private var icons: Icons? = null

    private val noteItemViewBinder = NoteItemViewBinder(context, true)

    interface OnClickListener {
        fun onItem(item: SettingsImportViewModel.Item)
        fun onButton(item: SettingsImportViewModel.Item)
    }

    class SettingsImportViewHolder(val binding: ItemSettingsImportBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingsImportViewHolder {
        val holder = SettingsImportViewHolder(ItemSettingsImportBinding.inflate(
            LayoutInflater.from(parent.context), parent, false))

        holder.binding.itemSettingsImportPayload.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onItem(getItem(holder.bindingAdapterPosition))
            }
        }

        holder.binding.itemSettingsImportButton.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onButton(getItem(holder.bindingAdapterPosition))
            }
        }

        return holder
    }

    override fun onBindViewHolder(holder: SettingsImportViewHolder, position: Int) {

        if (icons == null) {
            icons = Icons(R.drawable.ic_keyboard_arrow_up, R.drawable.ic_library_books)
        }

        val item = getItem(position)

        when (val payload = item.payload) {
            is Book -> {
                holder.binding.itemSettingsImportName.text = payload.title ?: payload.name

                holder.binding.itemSettingsImportButton.visibility = View.GONE

                holder.binding.itemSettingsImportIcon.visibility = View.VISIBLE
            }

            is Note -> {
                holder.binding.itemSettingsImportName.text = noteItemViewBinder.generateTitle(
                    NoteView(note = payload, bookName = ""))
                icons?.let {
                    if (payload.position.descendantsCount > 0) {
                        holder.binding.itemSettingsImportIcon.setImageResource(R.drawable.bullet_folded)
                        holder.binding.itemSettingsImportButton.visibility = View.GONE
                    } else {
                        holder.binding.itemSettingsImportIcon.setImageResource(R.drawable.bullet)
                        holder.binding.itemSettingsImportButton.visibility = View.VISIBLE
                    }
                    holder.binding.itemSettingsImportIcon.visibility = View.VISIBLE
                }
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<SettingsImportViewModel.Item> =
            object : DiffUtil.ItemCallback<SettingsImportViewModel.Item>() {
                override fun areItemsTheSame(oldItem: SettingsImportViewModel.Item, newItem: SettingsImportViewModel.Item): Boolean {
                    return oldItem == newItem
                }

                override fun areContentsTheSame(oldItem: SettingsImportViewModel.Item, newItem: SettingsImportViewModel.Item): Boolean {
                    return oldItem == newItem
                }
            }
    }
}