package com.lanecki.deepnoise.adapters

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lanecki.deepnoise.CallActivity
import com.lanecki.deepnoise.R
import com.lanecki.deepnoise.call.CallManager
import com.lanecki.deepnoise.call.CallState
import com.lanecki.deepnoise.databinding.ListItemContactBinding

class ContactsAdapter(private val contacts: List<String>) : RecyclerView.Adapter<ContactsAdapter.ContactViewHolder>() {

    class ContactViewHolder(private val binding: ListItemContactBinding, private val context: Context)
        : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val callee = binding.contactName.text

                val intent = Intent(it.context, CallActivity::class.java).apply {
                    putExtra(CallActivity.CALLEE_KEY, callee)
                    putExtra(CallActivity.INITIAL_STATE_KEY, CallState.OUTGOING)
                }
                it.context.startActivity(intent)
            }
        }

        fun bind(textContact: String) {
            binding.apply {
                contactName.text = textContact
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val contactView = ListItemContactBinding.inflate(
            LayoutInflater.from(parent.context), parent, false)
        return ContactViewHolder(contactView, parent.context)
    }

    override fun getItemCount() = contacts.size

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = contacts[position]
        holder.bind(contact)
    }
}