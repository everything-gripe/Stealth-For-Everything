package com.cosmos.unreddit.subreddit

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import coil.size.Precision
import coil.size.Scale
import com.cosmos.unreddit.R
import com.cosmos.unreddit.api.Resource
import com.cosmos.unreddit.base.BaseFragment
import com.cosmos.unreddit.databinding.FragmentSubredditBinding
import com.cosmos.unreddit.databinding.LayoutSubredditAboutBinding
import com.cosmos.unreddit.databinding.LayoutSubredditContentBinding
import com.cosmos.unreddit.loadstate.NetworkLoadStateAdapter
import com.cosmos.unreddit.post.PostEntity
import com.cosmos.unreddit.post.Sorting
import com.cosmos.unreddit.postlist.PostListAdapter
import com.cosmos.unreddit.postlist.PostListRepository
import com.cosmos.unreddit.postmenu.PostMenuFragment
import com.cosmos.unreddit.sort.SortFragment
import com.cosmos.unreddit.util.addLoadStateListener
import com.cosmos.unreddit.util.toPixels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SubredditFragment : BaseFragment(), View.OnClickListener {

    private var _binding: FragmentSubredditBinding? = null
    private val binding get() = _binding!!

    private var _bindingContent: LayoutSubredditContentBinding? = null
    private val bindingContent get() = _bindingContent!!

    private var _bindingAbout: LayoutSubredditAboutBinding? = null
    private val bindingAbout get() = _bindingAbout!!

    private val viewModel: SubredditViewModel by viewModels()

    private val args: SubredditFragmentArgs by navArgs()

    private var loadPostsJob: Job? = null

    private lateinit var postListAdapter: PostListAdapter

    @Inject
    lateinit var repository: PostListRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setSubreddit(args.subreddit)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSubredditBinding.inflate(inflater, container, false)
        _bindingContent = binding.subredditContent
        _bindingAbout = binding.subredditAbout
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initResultListener()
        initAppBar()
        initRecyclerView()
        initDrawer()
        bindViewModel()
        bindingAbout.subredditSubscribeButton.setOnClickListener(this)
        bindingContent.loadingState.infoRetry.setActionClickListener { retry() }
    }

    private fun bindViewModel() {
        viewModel.about.observe(viewLifecycleOwner) {
            when (it) {
                is Resource.Success -> bindInfo(it.data)
                is Resource.Error -> handleError(it.code)
                is Resource.Loading -> {
                    // ignore
                }
            }
        }
        viewModel.isSubscribed.observe(
            viewLifecycleOwner,
            { isSubscribed ->
                with(bindingAbout.subredditSubscribeButton) {
                    visibility = View.VISIBLE
                    text = if (isSubscribed) {
                        getString(R.string.subreddit_button_unsubscribe)
                    } else {
                        getString(R.string.subreddit_button_subscribe)
                    }
                }
            }
        )
        viewModel.isDescriptionCollapsed.observe(
            viewLifecycleOwner,
            { isCollapsed ->
                // TODO: Animate layout changes
                val maxHeight = if (isCollapsed) {
                    requireContext().toPixels(DESCRIPTION_MAX_HEIGHT).toInt()
                } else {
                    Integer.MAX_VALUE
                }
                ConstraintSet().apply {
                    clone(bindingAbout.layoutRoot)
                    constrainMaxHeight(R.id.subreddit_public_description, maxHeight)
                    applyTo(bindingAbout.layoutRoot)
                }
            }
        )
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            combine(
                viewModel.subreddit,
                viewModel.sorting,
                viewModel.contentPreferences
            ) { subreddit, sorting, contentPreferences ->
                bindingContent.loadingState.infoRetry.hide()
                postListAdapter.contentPreferences = contentPreferences
                subreddit?.let {
                    viewModel.loadSubredditInfo(false)
                    loadPosts(subreddit, sorting)
                }
                bindingContent.sortIcon.setSorting(sorting)
            }.collect { scrollToTop() }
        }
    }

    private fun initRecyclerView() {
        postListAdapter = PostListAdapter(repository, this, this).apply {
            addLoadStateListener(bindingContent.listPost, bindingContent.loadingState) {
                showRetryBar()
            }
        }
        bindingContent.listPost.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = postListAdapter.withLoadStateHeaderAndFooter(
                header = NetworkLoadStateAdapter { postListAdapter.retry() },
                footer = NetworkLoadStateAdapter { postListAdapter.retry() }
            )
        }

        lifecycleScope.launch {
            postListAdapter.loadStateFlow.distinctUntilChangedBy { it.refresh }
                .filter { it.refresh is LoadState.NotLoading }
                .collect { scrollToTop() }
        }
    }

    private fun initDrawer() {
        with(binding.drawerLayout) {
            setScrimColor(Color.TRANSPARENT)
            drawerElevation = 0F
        }
        bindingAbout.subredditPublicDescription.setOnClickListener {
            viewModel.toggleDescriptionCollapsed()
        }
    }

    private fun initAppBar() {
        with(bindingContent) {
            sortCard.setOnClickListener { showSortDialog() }
            backCard.setOnClickListener { onBackPressed() }
            searchCard.setOnClickListener { showSearchFragment() }
        }
    }

    private fun initResultListener() {
        childFragmentManager.setFragmentResultListener(
            SortFragment.REQUEST_KEY_SORTING,
            viewLifecycleOwner
        ) { _, bundle ->
            val sorting = bundle.getParcelable(SortFragment.BUNDLE_KEY_SORTING) as? Sorting
            sorting?.let { viewModel.setSorting(it) }
        }
    }

    private fun bindInfo(about: SubredditEntity) {
        with(about) {
            bindingContent.subreddit = this
            bindingAbout.subreddit = this

            bindingContent.subredditImage.load(icon) {
                crossfade(true)
                scale(Scale.FILL)
                precision(Precision.AUTOMATIC)
                placeholder(R.drawable.icon_reddit_placeholder)
                error(R.drawable.icon_reddit_placeholder)
                fallback(R.drawable.icon_reddit_placeholder)
            }

            if (publicDescription.isNotEmpty()) {
                bindingAbout.subredditPublicDescription.apply {
                    setText(publicDescription)
                    setOnLinkClickListener(this@SubredditFragment)
                }
            } else {
                bindingAbout.subredditPublicDescription.visibility = View.GONE
            }
            if (description.isNotEmpty()) {
                bindingAbout.subredditDescription.apply {
                    setText(description)
                    setOnLinkClickListener(this@SubredditFragment)
                }
            }
        }
    }

    private fun loadPosts(subreddit: String, sorting: Sorting) {
        loadPostsJob?.cancel()
        loadPostsJob = viewLifecycleOwner.lifecycleScope.launch {
            viewModel.loadAndFilterPosts(subreddit, sorting).collectLatest {
                postListAdapter.submitData(it)
            }
        }
    }

    private fun handleError(code: Int?) {
        when (code) {
            403 -> showUnauthorizedDialog()
            404 -> showNotFoundDialog()
            else -> showRetryBar()
        }
    }

    private fun retry() {
        viewModel.about.value?.let {
            if (it is Resource.Error) {
                viewModel.loadSubredditInfo(true)
            }
        }

        postListAdapter.retry() // TODO: Don't retry if not necessary
    }

    private fun showRetryBar() {
        if (!bindingContent.loadingState.infoRetry.isVisible) {
            bindingContent.loadingState.infoRetry.show()
        }
    }

    private fun scrollToTop() {
        // TODO: Find better method when item is too far
        bindingContent.listPost.scrollToPosition(0)
    }

    private fun showSearchFragment() {
        navigate(
            SubredditFragmentDirections.openSearch(
                viewModel.subreddit.value!!,
                viewModel.about.value?.dataValue?.icon
            )
        )
    }

    private fun showSortDialog() {
        SortFragment.show(childFragmentManager, viewModel.sorting.value)
    }

    private fun showNotFoundDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_subreddit_not_found_title)
            .setMessage(R.string.dialog_subreddit_not_found_body)
            .setPositiveButton(R.string.dialog_ok) { _, _ -> onBackPressed() }
            .setCancelable(false)
            .show()
    }

    private fun showUnauthorizedDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_subreddit_unauthorized_title)
            .setMessage(R.string.dialog_subreddit_unauthorized_body)
            .setPositiveButton(R.string.dialog_ok) { _, _ -> onBackPressed() }
            .setCancelable(false)
            .show()
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.END)) {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        _bindingContent = null
        _bindingAbout = null
    }

    override fun onLongClick(post: PostEntity) {
        PostMenuFragment.show(parentFragmentManager, post, PostMenuFragment.MenuType.SUBREDDIT)
    }

    override fun onMenuClick(post: PostEntity) {
        PostMenuFragment.show(parentFragmentManager, post, PostMenuFragment.MenuType.SUBREDDIT)
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            bindingAbout.subredditSubscribeButton.id -> {
                viewModel.toggleSubscription()
            }
        }
    }

    companion object {
        private const val DESCRIPTION_MAX_HEIGHT = 200F
    }
}
