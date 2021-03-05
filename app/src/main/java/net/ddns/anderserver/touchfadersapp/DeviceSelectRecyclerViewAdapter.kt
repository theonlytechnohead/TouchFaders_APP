package net.ddns.anderserver.touchfadersapp

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView

class DeviceSelectRecyclerViewAdapter internal constructor(context: Context?, private val deviceNames: MutableList<String>) : RecyclerView.Adapter<DeviceSelectRecyclerViewAdapter.ViewHolder>() {

    private var clickListener: DeviceButtonClickListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.recyclerview_device_selection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.deviceSelectButton.text = deviceNames[position]
        holder.deviceSelectButton.isEnabled = true
        holder.devicePosition = position
    }

    fun addDevice(name: String) {
        if (!deviceNames.contains(name)) {
            deviceNames.add(deviceNames.size, name)
            this.notifyItemInserted(deviceNames.size - 1)
        }
    }

    fun removeDevice(name: String) {
        if (deviceNames.contains(name)) {
            this.notifyItemRemoved(deviceNames.indexOf(name))
            deviceNames.remove(name)
        }
    }

    override fun getItemCount(): Int {
        return deviceNames.size
    }

    inner class ViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView), DeviceButtonClickListener {
        var devicePosition = 0

        //TextView mixTextView;
        var deviceSelectButton: Button = itemView.findViewById(R.id.device_select_button)
        override fun onItemClick(view: View?, index: Int) {
            if (clickListener != null) clickListener!!.onItemClick(view, index)
        }

        init {
            //mixTextView = itemView.findViewById(R.id.mix_select_text);
            //mixTextView.setOnClickListener(this);
            deviceSelectButton.setOnClickListener { view: View? ->
                deviceSelectButton.isEnabled = false
                onItemClick(view, devicePosition)
            }
        }
    }

    // convenience method for getting data at click position
    fun getDeviceName(id: Int): String {
        return deviceNames[id]
    }

    // allows clicks events to be caught
    fun setClickListener(onClickListener: DeviceButtonClickListener?) {
        clickListener = onClickListener
    }

    // parent activity will implement this method to respond to click events
    interface DeviceButtonClickListener {
        fun onItemClick(view: View?, index: Int)
    }
}