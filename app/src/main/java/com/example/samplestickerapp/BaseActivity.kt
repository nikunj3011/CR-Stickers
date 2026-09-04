/*
 * Copyright (c) WhatsApp Inc. and its affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.samplestickerapp

import android.app.Dialog
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment

abstract class BaseActivity : AppCompatActivity() {
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    class MessageDialogFragment : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val title = requireArguments().getInt(ARG_TITLE_ID)
            val message = requireArguments().getString(ARG_MESSAGE)
            return AlertDialog.Builder(requireContext())
                .setMessage(message)
                .setCancelable(true)
                .setPositiveButton(android.R.string.ok) { _, _ -> dismiss() }
                .apply { if (title != 0) setTitle(title) }
                .create()
        }

        companion object {
            private const val ARG_TITLE_ID = "title_id"
            private const val ARG_MESSAGE = "message"

            @JvmStatic
            fun newInstance(@StringRes titleId: Int, message: String?): DialogFragment =
                MessageDialogFragment().apply {
                    arguments = Bundle().apply {
                        putInt(ARG_TITLE_ID, titleId)
                        putString(ARG_MESSAGE, message)
                    }
                }
        }
    }
}
