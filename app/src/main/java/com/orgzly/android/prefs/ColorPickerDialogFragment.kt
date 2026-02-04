package com.orgzly.android.prefs

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import androidx.preference.PreferenceDialogFragmentCompat
import com.orgzly.R
import com.orgzly.databinding.DialogColorPickerBinding
import kotlin.math.pow

class ColorPickerDialogFragment : PreferenceDialogFragmentCompat() {
    
    private var _binding: DialogColorPickerBinding? = null
    private val binding get() = _binding!!
    
    private var selectedColor: Int = Color.RED
    
    private val availableColors = intArrayOf(
        0xFFF44336.toInt(), // Red
        0xFFE91E63.toInt(), // Pink
        0xFF9C27B0.toInt(), // Purple
        0xFF673AB7.toInt(), // Deep Purple
        0xFF3F51B5.toInt(), // Indigo
        0xFF2196F3.toInt(), // Blue
        0xFF03A9F4.toInt(), // Light Blue
        0xFF00BCD4.toInt(), // Cyan
        0xFF009688.toInt(), // Teal
        0xFF4CAF50.toInt(), // Green
        0xFF8BC34A.toInt(), // Light Green
        0xFFCDDC39.toInt(), // Lime
        0xFFFFEB3B.toInt(), // Yellow
        0xFFFFC107.toInt(), // Amber
        0xFFFF9800.toInt(), // Orange
        0xFFFF5722.toInt(), // Deep Orange
        0xFF795548.toInt(), // Brown
        0xFF9E9E9E.toInt(), // Grey
        0xFF607D8B.toInt(), // Blue Grey
        0xFF000000.toInt()  // Black
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get the preference
        val preference = preference as? ColorPickerPreference
            ?: throw IllegalStateException("ColorPickerPreference not found")
        selectedColor = preference.getColor()
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        _binding = DialogColorPickerBinding.bind(view)
        
        // Show color preview and hex text
        binding.colorHexText.visibility = View.VISIBLE
        updateColorPreview()
        
        binding.colorGrid.adapter = ColorAdapter()
        binding.colorGrid.setOnItemClickListener { _, _, position, _ ->
            selectedColor = availableColors[position]
            updateColorPreview()
            (binding.colorGrid.adapter as ColorAdapter).notifyDataSetChanged()
        }
    }

    private fun updateColorPreview() {
        binding.colorPreview.setBackgroundColor(selectedColor)
        binding.colorHexText.text = String.format("#%06X", 0xFFFFFF and selectedColor)
        binding.colorHexText.setTextColor(if (calculateLuminance(selectedColor) > 0.5) Color.BLACK else Color.WHITE)
    }

    private fun calculateLuminance(color: Int): Double {
        val r = Color.red(color) / 255.0
        val g = Color.green(color) / 255.0
        val b = Color.blue(color) / 255.0
        
        // Apply gamma correction
        val linearR = if (r <= 0.03928) r / 12.92 else ((r + 0.055) / 1.055).pow(2.4)
        val linearG = if (g <= 0.03928) g / 12.92 else ((g + 0.055) / 1.055).pow(2.4)
        val linearB = if (b <= 0.03928) b / 12.92 else ((b + 0.055) / 1.055).pow(2.4)
        
        // Calculate luminance using standard formula
        return 0.2126 * linearR + 0.7152 * linearG + 0.0722 * linearB
    }

    inner class ColorAdapter : BaseAdapter() {
        override fun getCount(): Int = availableColors.size
        override fun getItem(position: Int): Any = availableColors[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val imageView = (convertView as? ImageView) ?: ImageView(context).apply {
                 layoutParams = android.widget.AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    resources.getDimensionPixelSize(R.dimen.color_swatch_size)
                )
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(8, 8, 8, 8)
            }

            val color = availableColors[position]
            imageView.setBackgroundColor(color)
            
            if (color == selectedColor) {
                imageView.setImageResource(R.drawable.ic_check_white_24dp)
            } else {
                imageView.setImageDrawable(null)
            }

            return imageView
        }
    }
    
    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            val preference = preference as? ColorPickerPreference
            preference?.setColor(selectedColor)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        val FRAGMENT_TAG: String = ColorPickerDialogFragment::class.java.name
        
        fun newInstance(preferenceKey: String): ColorPickerDialogFragment {
            val fragment = ColorPickerDialogFragment()
            val args = Bundle().apply {
                putString(ARG_KEY, preferenceKey)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
