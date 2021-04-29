package com.gno.smbvideoplayer.main

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.core.content.ContextCompat
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.gno.smbvideoplayer.R
import com.gno.smbvideoplayer.setting.SettingActivity
import jcifs.smb.SmbFile

class MainFragment : BrowseSupportFragment() {

    private lateinit var mainViewModel: MainViewModel
    private lateinit var listSmbFiles: List<SmbFile>

    private lateinit var mBackgroundManager: BackgroundManager
    private lateinit var mDefaultBackground: Drawable
    private lateinit var mMetrics: DisplayMetrics


    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        mainViewModel = ViewModelProvider(this).get(MainViewModel::class.java)

        prepareBackgroundManager()
        setupUIElements()
        loadRows()
        setupEventListeners()

    }

    private fun prepareBackgroundManager() {

        activity?.let {
            mBackgroundManager = BackgroundManager.getInstance(it)
            mBackgroundManager.attach(it.window)
            mMetrics = DisplayMetrics()
            it.windowManager.defaultDisplay.getMetrics(mMetrics)
            }

    }

    private fun setupUIElements() {
        title = getString(R.string.browse_title)

        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true

        activity?.let {
            brandColor = ContextCompat.getColor(it, R.color.fastlane_background)
            searchAffordanceColor = ContextCompat.getColor(it, R.color.search_opaque)
        }
    }

    private fun loadRows() {

        val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        val cardPresenter = CardPresenter()

        rowsAdapter.add(
            ListRow(
                HeaderItem(0, getString(R.string.my_movies)),
                ArrayObjectAdapter(cardPresenter)
            )
        )

        val gridRowAdapter = ArrayObjectAdapter(GridItemPresenter())
        gridRowAdapter.add(getString(R.string.Connection))

        rowsAdapter.add(ListRow(HeaderItem(0, getString(R.string.Settings)), gridRowAdapter))
        adapter = rowsAdapter

        mainViewModel.getListFiles()

        mainViewModel.popularMoviesLiveData.observe(viewLifecycleOwner, {
            listSmbFiles = it.listFiles
            addListFilesRow(it.parent)
        })

        val sharedPreferences: SharedPreferences = PreferenceManager
            .getDefaultSharedPreferences(activity)
        mainViewModel.initSharedPreferenceStringLiveDataUrl(sharedPreferences)
        mainViewModel.sharedPreferenceStringLiveDataUrl.getStringLiveData("setting_url", "")
            .observe(viewLifecycleOwner, {
                mainViewModel.url = "smb://$it/"
                mainViewModel.getListFiles()
            })
        mainViewModel.sharedPreferenceStringLiveDataUserName.getStringLiveData(
            "setting_username",
            ""
        ).observe(viewLifecycleOwner, {
            mainViewModel.username = it
            mainViewModel.getListFiles()
        })
        mainViewModel.sharedPreferenceStringLiveDataPassword.getStringLiveData(
            "setting_password",
            ""
        ).observe(viewLifecycleOwner, {
            mainViewModel.password = it
            mainViewModel.getListFiles()
        })

    }

    private fun addListFilesRow(parent: SmbFile?) {

        if (listSmbFiles.isEmpty()) {
            return
        }

        val listRowAdapter = ArrayObjectAdapter(CardPresenter())

        if (parent != null) {
            listRowAdapter.add(parent)
        }

        for (smbFile: SmbFile in listSmbFiles) {
            listRowAdapter.add(smbFile)
        }

        val arrayObjectAdapter = adapter as ArrayObjectAdapter
        arrayObjectAdapter.removeItems(0, 1)
        arrayObjectAdapter.add(
            0, ListRow(
                HeaderItem(0, "My movies"),
                listRowAdapter
            )
        )

        arrayObjectAdapter.notifyItemRangeChanged(0, 1)

    }

    private fun setupEventListeners() {
        onItemViewClickedListener = ItemViewClickedListener()
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder,
            item: Any,
            rowViewHolder: RowPresenter.ViewHolder,
            row: Row
        ) {

            if (item is SmbFile) {

                mainViewModel.processFileSelection(item, activity!!)

            } else if (item is String) {
                if (item.contains("Сonnection")) {
                    val intent = Intent(activity, SettingActivity::class.java)
                    startActivity(intent)
                }
            }
        }
    }


    private inner class GridItemPresenter : Presenter() {
        override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
            val view = TextView(parent.context)
            view.layoutParams = ViewGroup.LayoutParams(GRID_ITEM_WIDTH, GRID_ITEM_HEIGHT)
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.setBackgroundColor(
                ContextCompat.getColor(
                    requireActivity(),
                    R.color.default_background
                )
            )
            view.setTextColor(Color.WHITE)
            view.gravity = Gravity.CENTER
            return ViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
            (viewHolder.view as TextView).text = item as String
        }

        override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
    }

    companion object {

        private const val GRID_ITEM_WIDTH = 200
        private const val GRID_ITEM_HEIGHT = 200

    }
}