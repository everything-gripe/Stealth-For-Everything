package com.cosmos.unreddit.ui.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmos.unreddit.data.model.Comment
import com.cosmos.unreddit.data.model.db.PostEntity
import com.cosmos.unreddit.data.model.db.Profile
import com.cosmos.unreddit.data.model.db.Subscription
import com.cosmos.unreddit.data.repository.PostListRepository
import com.cosmos.unreddit.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

open class BaseViewModel(
    preferencesRepository: PreferencesRepository,
    private val postListRepository: PostListRepository
) : ViewModel() {

    val currentProfile: Flow<Profile> = preferencesRepository.getCurrentProfile().map {
        postListRepository.getProfile(it)
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(), 1)

    protected val historyIds: Flow<List<String>> = currentProfile.flatMapMerge {
        postListRepository.getHistoryIds(it.id).distinctUntilChanged()
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(), 1)

    protected val subscriptions: Flow<List<Subscription>> = currentProfile.flatMapMerge {
        postListRepository.getSubscriptions(it.id).distinctUntilChanged()
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(), 1)

    protected val subscriptionsNames: Flow<List<String>> = currentProfile.flatMapMerge {
        postListRepository.getSubscriptionsNames(it.id).distinctUntilChanged()
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(), 1)

    protected val savedPostIds: Flow<List<String>> = currentProfile.flatMapMerge {
        postListRepository.getSavedPostIds(it.id).distinctUntilChanged()
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(), 1)

    fun toggleSavePost(post: PostEntity) {
        viewModelScope.launch {
            currentProfile.first().let {
                if (post.saved) {
                    postListRepository.unsavePost(post, it.id)
                } else {
                    postListRepository.savePost(post, it.id)
                }
            }
        }
    }

    fun toggleSaveComment(comment: Comment.CommentEntity) {
        viewModelScope.launch {
            currentProfile.first().let {
                if (comment.saved) {
                    postListRepository.unsaveComment(comment, it.id)
                } else {
                    postListRepository.saveComment(comment, it.id)
                }
            }
        }
    }
}