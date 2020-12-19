package com.cosmos.unreddit.postlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cosmos.unreddit.R
import com.cosmos.unreddit.api.RedditApi
import com.cosmos.unreddit.databinding.FragmentPostBinding
import com.cosmos.unreddit.post.PostEntity
import com.cosmos.unreddit.post.Sorting
import com.cosmos.unreddit.postdetails.PostDetailsFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PostListFragment : Fragment(), PostListAdapter.PostClickListener {

    private var _binding: FragmentPostBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PostListViewModel by activityViewModels()

    private var loadPostsJob: Job? = null

    private lateinit var adapter: PostListAdapter

    @Inject
    lateinit var repository: PostListRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPostBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initAppBar()
        initRecyclerView()
        bindViewModel()
    }

    private fun bindViewModel() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            combine(viewModel.subreddit, viewModel.sorting) { subreddit, sorting ->
                loadPosts(subreddit, sorting)
                setSortIcon(sorting)
            }.collect { scrollToTop() } // TODO: Sometimes does not scroll to top
        }
    }

    private fun initRecyclerView() {
        adapter = PostListAdapter(repository, this)
        binding.listPost.layoutManager = LinearLayoutManager(requireContext())
        binding.listPost.adapter = adapter
    }

    private fun initAppBar() {
        binding.appBar.sortCard.setOnClickListener { showSortDialog() }
    }

    private fun setSortIcon(sorting: Sorting) {
        val popInAnimation = AnimationUtils.loadAnimation(context, R.anim.pop_in)
        val popOutAnimation = AnimationUtils.loadAnimation(context, R.anim.pop_out)

        with(binding.appBar.sortIcon) {
            visibility = if (sorting.generalSorting == RedditApi.Sort.HOT) {
                View.GONE
            } else {
                View.VISIBLE
            }

            when (sorting.generalSorting) {
                RedditApi.Sort.NEW -> setImageResource(R.drawable.ic_new)
                RedditApi.Sort.TOP -> setImageResource(R.drawable.ic_top)
                RedditApi.Sort.RISING -> setImageResource(R.drawable.ic_rising)
                RedditApi.Sort.CONTROVERSIAL -> setImageResource(R.drawable.ic_controversial)
                else -> {
                    startAnimation(popOutAnimation)
                    return@with
                }
            }

            startAnimation(popInAnimation)
        }

        with(binding.appBar.sortTimeText) {
            visibility = if (sorting.generalSorting == RedditApi.Sort.TOP ||
                sorting.generalSorting == RedditApi.Sort.CONTROVERSIAL
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }

            text = when (sorting.timeSorting) {
                RedditApi.TimeSorting.HOUR -> getString(R.string.sort_time_hour_short)
                RedditApi.TimeSorting.DAY -> getString(R.string.sort_time_day_short)
                RedditApi.TimeSorting.WEEK -> getString(R.string.sort_time_week_short)
                RedditApi.TimeSorting.MONTH -> getString(R.string.sort_time_month_short)
                RedditApi.TimeSorting.YEAR -> getString(R.string.sort_time_year_short)
                RedditApi.TimeSorting.ALL -> getString(R.string.sort_time_all_short)
                null -> {
                    startAnimation(popOutAnimation)
                    return@with
                }
            }

            startAnimation(popInAnimation)
        }
    }

    private fun loadPosts(subreddit: String, sorting: Sorting) {
        loadPostsJob?.cancel()
        loadPostsJob = viewLifecycleOwner.lifecycleScope.launch {
            viewModel.loadAndFilterPosts(subreddit, sorting).collectLatest {
                adapter.submitData(it)
            }
        }
    }

    fun scrollToTop() {
        // TODO: Find better method when item is too far
        binding.listPost.smoothScrollToPosition(0)
    }

    private fun showSortDialog() {
        SortFragment.show(childFragmentManager)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onClick(post: PostEntity) {
        val postDetailsFragment = PostDetailsFragment.newInstance(post)
        postDetailsFragment.show(childFragmentManager, PostDetailsFragment.TAG)
    }

    override fun onLongClick(post: PostEntity) {
        Toast.makeText(context, post.domain, Toast.LENGTH_SHORT).show() // TODO
    }

    override fun onImageClick(post: PostEntity) {
        Toast.makeText(context, post.preview, Toast.LENGTH_SHORT).show() // TODO
    }

    override fun onVideoClick(post: PostEntity) {
        Toast.makeText(context, post.preview, Toast.LENGTH_SHORT).show() // TODO
    }

    override fun onLinkClick(post: PostEntity) {
        Toast.makeText(context, post.url, Toast.LENGTH_SHORT).show() // TODO
    }

    companion object {
        const val TAG = "PostListFragment"

        @JvmStatic
        fun newInstance() = PostListFragment()
    }
}