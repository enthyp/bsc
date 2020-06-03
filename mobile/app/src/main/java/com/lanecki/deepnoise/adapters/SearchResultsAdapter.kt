package com.lanecki.deepnoise.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.lanecki.deepnoise.databinding.ListItemSearchedUserBinding
import com.lanecki.deepnoise.model.User


class SearchResultsAdapter : RecyclerView.Adapter<SearchResultsAdapter.UserViewHolder>() {

    class UserViewHolder(
        private val binding: ListItemSearchedUserBinding,
        private val context: Context
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                Toast.makeText(context, binding.userLogin.text, Toast.LENGTH_LONG).show()
            }
        }

        fun bind(user: User) {
            binding.apply {
                userLogin.text = user.login
            }
        }
    }

    class ResultsDiffCallback(
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

    private val results: MutableList<User> = mutableListOf()

    fun updateResults(update: List<User>) {
        val diffCallback = ResultsDiffCallback(results, update)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        results.clear()
        results.addAll(update)
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val contactView = ListItemSearchedUserBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return UserViewHolder(contactView, parent.context)
    }

    override fun getItemCount() = results.size

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = results[position]
        holder.bind(user)
    }
}