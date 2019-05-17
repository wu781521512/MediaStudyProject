package com.example.mediastudyproject.adapter

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.example.mediastudyproject.R
import com.example.mediastudyproject.bean.Song

class ListAdapter(private val context: Context, private var data: ArrayList<Song>) : BaseAdapter() {

    override fun getView(position: Int,convertView: View?, parent: ViewGroup?): View {
        var viewHolder: ViewHolder? = null
        var v: View
        if (convertView == null) {
            v = View.inflate(context, R.layout.item_layout,null)
            viewHolder = ViewHolder(v)
            v.tag = viewHolder
        }else{
            v = convertView
            viewHolder = v.tag as ViewHolder
        }

        viewHolder.text_name.text = data[position].name
        return v
    }

    override fun getItem(position: Int) = data[position]

    override fun getItemId(position: Int) = position.toLong()

    override fun getCount() = data.size


   class ViewHolder(var view: View){
       var text_name = view.findViewById<TextView>(R.id.tv_name)
   }
}