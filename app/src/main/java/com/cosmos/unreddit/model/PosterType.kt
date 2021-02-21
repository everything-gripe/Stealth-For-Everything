package com.cosmos.unreddit.model

import android.content.Context
import com.cosmos.unreddit.R
import com.cosmos.unreddit.util.PostUtil

enum class PosterType(val value: Int) {
    REGULAR(0), ADMIN(1), MODERATOR(2);

    companion object {
        fun toType(type: Int): PosterType {
            return when (type) {
                REGULAR.value -> REGULAR
                ADMIN.value -> ADMIN
                MODERATOR.value -> MODERATOR
                else -> REGULAR
            }
        }

        fun fromDistinguished(distinguished: String?): PosterType {
            return when (distinguished) {
                "admin" -> ADMIN
                "moderator" -> MODERATOR
                else -> REGULAR
            }
        }

        fun getGradientColors(context: Context, posterType: PosterType): IntArray {
            return when (posterType) {
                REGULAR -> PostUtil.getAuthorGradientColor(
                    context,
                    R.color.regular_gradient_start,
                    R.color.regular_gradient_end
                )
                ADMIN -> PostUtil.getAuthorGradientColor(
                    context,
                    R.color.admin_gradient_start,
                    R.color.admin_gradient_end
                )
                MODERATOR -> PostUtil.getAuthorGradientColor(
                    context,
                    R.color.moderator_gradient_start,
                    R.color.moderator_gradient_end
                )
            }
        }
    }
}