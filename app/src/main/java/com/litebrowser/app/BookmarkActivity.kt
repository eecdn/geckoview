package com.litebrowser.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch

/**
 * 书签管理页面
 */
class BookmarkActivity : AppCompatActivity() {

    private lateinit var database: BrowserDatabase
    private lateinit var adapter: BookmarkAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookmark)

        database = BrowserDatabase(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_bookmark)
        toolbar.setNavigationOnClickListener { finish() }

        val recyclerView = findViewById<RecyclerView>(R.id.bookmark_list)
        val emptyView = findViewById<TextView>(R.id.empty_bookmark)

        adapter = BookmarkAdapter(
            onBookmarkClick = { url ->
                val intent = Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = android.net.Uri.parse(url)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
                finish()
            },
            onBookmarkDelete = { item ->
                lifecycleScope.launch {
                    database.removeBookmark(item.id)
                    loadBookmarks()
                }
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        loadBookmarks()
    }

    private fun loadBookmarks() {
        lifecycleScope.launch {
            val bookmarks = database.getAllBookmarks()
            val recyclerView = findViewById<RecyclerView>(R.id.bookmark_list)
            val emptyView = findViewById<TextView>(R.id.empty_bookmark)

            if (bookmarks.isEmpty()) {
                recyclerView.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyView.visibility = View.GONE
                adapter.submitList(bookmarks)
            }
        }
    }

    class BookmarkAdapter(
        private val onBookmarkClick: (String) -> Unit,
        private val onBookmarkDelete: (BrowserDatabase.BookmarkItem) -> Unit
    ) : RecyclerView.Adapter<BookmarkAdapter.ViewHolder>() {

        private val items = mutableListOf<BrowserDatabase.BookmarkItem>()

        fun submitList(newItems: List<BrowserDatabase.BookmarkItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_bookmark_history, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.title.text = item.title
            holder.url.text = item.url
            holder.itemView.setOnClickListener { onBookmarkClick(item.url) }
            holder.delete.setOnClickListener { onBookmarkDelete(item) }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.item_title)
            val url: TextView = view.findViewById(R.id.item_url)
            val delete: ImageButton = view.findViewById(R.id.item_delete)
        }
    }
}
