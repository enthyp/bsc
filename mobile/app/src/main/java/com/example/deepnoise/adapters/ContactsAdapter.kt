package com.example.deepnoise.adapters

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.deepnoise.CallActivity
import com.example.deepnoise.R
import com.example.deepnoise.databinding.ListItemContactBinding

class ContactsAdapter(private val contacts: List<String>) : RecyclerView.Adapter<ContactsAdapter.ContactViewHolder>() {

    class ContactViewHolder(private val binding: ListItemContactBinding, private val context: Context)
        : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val calleeKey = context.resources.getString(R.string.callee)
                val callee = binding.contactName.text

                val intent = Intent(it.context, CallActivity::class.java).apply {
                    putExtra(calleeKey, callee)
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