package ai.doc.netrunner.outputhandler

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import ai.doc.netrunner.R
import android.graphics.Bitmap
import android.widget.TextView

/**
 * A simple [Fragment] subclass.
 * Default output handler for model outputs, used when no other output handler has been registered
 * for the type or if no model type has been specified
 */

class DefaultOutputHandler : Fragment(), OutputHandler {

    companion object {
        val type = "NONE"
    }

    private lateinit var predictionTextView: TextView

    override var output: Map<String, Any>? = null
        set(value) {
            processOutput(value)
            field = value
        }

    // View Management

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_default_output_handler, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        predictionTextView = view.findViewById(R.id.predictions)
    }

    // Output Processing

    /** Format the output as best we can without making assumptions about its structure */

    private fun processOutput(output: Map<String, Any>?) {
        output?.let { o ->
            val formattedText = formattedOutput(o)
            predictionTextView.text = formattedText
        }
    }

    private fun formattedOutput(output: Map<String, Any>): String {
        val b = StringBuilder()

        for (key in output.keys.sorted()) {
            val value = output[key]
            b.append(key)
            b.append(": ")

            when (value) {
                is FloatArray -> {
                    b.append(formattedArray(value))
                }
                is IntArray -> {
                    b.append(formattedArray(value))
                }
                is ByteArray -> {
                    b.append(formattedArray(value))
                }
                is Bitmap -> {
                    b.append("<Image> (${value.width}x${value.height})")
                }
                is Map<*, *> -> {
                    b.append("<Map> (${value.size} entries)")
                }
                else -> {
                    b.append("<Unknown>")
                }
            }

            b.append("\n")
        }

        if (b.isNotEmpty()) {
            b.setLength(b.length - 1)
        }

        return b.toString()
    }

    private fun formattedArray(array: FloatArray): String {
        val b = StringBuilder()

        if (array.size > 1) {
            b.append("[")
        }

        for (v in array) {
            b.append(String.format("%.2f", v))
            b.append(", ")
        }

        if (b.length > 2) {
            b.delete(b.length-2, b.length)
        }

        if (array.size > 1) {
            b.append("]")
        }

        return b.toString()
    }

    private fun formattedArray(array: IntArray): String {
        val b = StringBuilder()

        if (array.size > 1) {
            b.append("[")
        }

        for (v in array) {
            b.append(v)
            b.append(", ")
        }

        if (b.length > 2) {
            b.delete(b.length-2, b.length)
        }

        if (array.size > 1) {
            b.append("]")
        }

        return b.toString()
    }

    private fun formattedArray(array: ByteArray): String {
        val b = StringBuilder()

        if (array.size > 1) {
            b.append("[")
        }

        for (v in array) {
            b.append(String.format("%02X", v))
            b.append(", ")
        }

        if (b.length > 2) {
            b.delete(b.length-2, b.length)
        }

        if (array.size > 1) {
            b.append("]")
        }

        return b.toString()
    }
}
