package com.lanecki.deepnoise.adapters

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.lanecki.deepnoise.CallActivity
import com.lanecki.deepnoise.utils.Constants
import com.lanecki.deepnoise.call.CallState
import com.lanecki.deepnoise.databinding.ListItemContactBinding
import com.lanecki.deepnoise.model.User

// TODO: reduce code duplication (search results)
class ContactsAdapter : RecyclerView.Adapter<ContactsAdapter.ContactViewHolder>() {

    class ContactViewHolder(private val binding: ListItemContactBinding, private val context: Context)
        : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val callee = binding.contactName.text

                val intent = Intent(it.context, CallActivity::class.java).apply {
                    putExtra(Constants.CALLEE_KEY, callee)
                    putExtra(Constants.INITIAL_STATE_KEY, CallState.OUTGOING)
                }
                it.context.startActivity(intent)
            }
        }

        fun bind(user: User) {
            binding.apply {
                contactName.text = user.login
            }
        }
    }

    // NOTE: could do without it (just notifyOnDataSetChanged() in updateContacts)
    class ContactsDiffCallback(
        private val oldUserList: List<User>,
        private val newUserList: List<User>
    ) :
        DiffUtil.Callback() {
        override fun getOldListSize() = oldUserList.size

        override fun getNewListSize() = newUserList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
            oldUserList[oldItemPosition] === newUserList[newItemPosition]

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
            oldUserList[oldItemPosition].login == newUserList[newItemPosition].login
    }

    private val contacts: MutableList<User> = mutableListOf()

    fun updateContacts(update: List<User>) {
        val diffCallback = ContactsDiffCallback(contacts, update)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        contacts.clear()
        contacts.addAll(update)
        diffResult.dispatchUpdatesTo(this)
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