package com.gnzalobnites.appsusagemonitor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class SimpleFragment : Fragment() {

    companion object {
        private const val ARG_TITLE = "title"
        
        fun newInstance(title: String): SimpleFragment {
            val fragment = SimpleFragment()
            val args = Bundle()
            args.putString(ARG_TITLE, title)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_simple, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val title = arguments?.getString(ARG_TITLE) ?: "Fragment"
        view.findViewById<TextView>(R.id.textView).text = "$title\n\n(En desarrollo - Próximamente)"
    }
}