package com.dp.logcatapp.fragments.logcatlive

import android.arch.lifecycle.ViewModelProviders
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.design.widget.FloatingActionButton
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SearchView
import android.view.*
import com.dp.logcat.Log
import com.dp.logcat.LogcatEventListener
import com.dp.logcatapp.R
import com.dp.logcatapp.activities.BaseActivity
import com.dp.logcatapp.fragments.base.BaseFragment
import com.dp.logcatapp.services.LogcatService
import com.dp.logcatapp.util.ServiceBinder
import com.dp.logcatapp.util.inflateLayout
import com.dp.logcatapp.util.showToast
import com.dp.logger.MyLogger
import kotlinx.android.synthetic.main.app_bar.*

class LogcatLiveFragment : BaseFragment(), ServiceConnection {
    private lateinit var serviceBinder: ServiceBinder
    private var logcatService: LogcatService? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var viewModel: LogcatLiveViewModel
    private lateinit var adapter: MyRecyclerViewAdapter
    private lateinit var fabUp: FloatingActionButton
    private lateinit var fabDown: FloatingActionButton
    private var ignoreScrollEvent = false

    private val logcatEventListener = object : LogcatEventListener {

        override fun onStartEvent() {
            activity.showToast("Logcat started")
            adapter.clear()
        }

        override fun onPreLogEvent(log: Log) {
            // do nothing
        }

        override fun onLogEvent(log: Log) {
            adapter.addItem(log)
            updateUIOnLogEvent(adapter.itemCount)
        }

        override fun onLogEvents(logs: List<Log>) {
            adapter.addItems(logs)
            updateUIOnLogEvent(adapter.itemCount)
        }

        override fun onStartFailedEvent() {
            activity.showToast("Failed to run logcat")
        }

        override fun onStopEvent() {
            activity.showToast("Logcat stopped")
        }
    }

    private val hideFabUpRunnable: Runnable = Runnable {
        fabUp.hide()
    }

    private val hideFabDownRunnable: Runnable = Runnable {
        fabDown.hide()
    }

    private val onScrollListener = object : RecyclerView.OnScrollListener() {
        var lastDy = 0

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (dy > 0 && lastDy <= 0) {
                hideFabUp()
                showFabDown()
            } else if (dy < 0 && lastDy >= 0) {
                showFabUp()
                hideFabDown()
            }
            lastDy = dy
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            when (newState) {
                RecyclerView.SCROLL_STATE_DRAGGING -> {
                    viewModel.autoScroll = false
                    if (lastDy > 0) {
                        hideFabUp()
                        showFabDown()
                    } else if (lastDy < 0) {
                        showFabUp()
                        hideFabDown()
                    }
                }
                else -> {
                    val pos = linearLayoutManager.findLastCompletelyVisibleItemPosition()
                    if (ignoreScrollEvent) {
                        if (pos == adapter.itemCount) {
                            ignoreScrollEvent = false
                        }
                        return
                    }

                    if (pos == 0) {
                        hideFabUp()
                    }

                    viewModel.scrollPosition = pos
                    viewModel.autoScroll = pos >= adapter.itemCount - 1
                    if (viewModel.autoScroll) {
                        hideFabUp()
                        hideFabDown()
                    }
//                    else if (lastDy < 0) {
//                        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
//                            fabDown.show()
//                        } else {
//                            fabDown.hide()
//                        }
//                    } else if (lastDy > 0) {
//                        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
//                            fabUp.show()
//                        } else {
//                            fabUp.hide()
//                        }
//                    }
                }
            }
        }
    }

    private fun showFabUp() {
        handler.removeCallbacks(hideFabUpRunnable)
        fabUp.show()
        handler.postDelayed(hideFabUpRunnable, 2000)
    }

    private fun hideFabUp() {
        handler.removeCallbacks(hideFabUpRunnable)
        fabUp.hide()
    }

    private fun showFabDown() {
        handler.removeCallbacks(hideFabDownRunnable)
        fabDown.show()
        handler.postDelayed(hideFabDownRunnable, 2000)
    }

    private fun hideFabDown() {
        handler.removeCallbacks(hideFabDownRunnable)
        fabDown.hide()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        viewModel = ViewModelProviders.of(this)
                .get(LogcatLiveViewModel::class.java)
        adapter = MyRecyclerViewAdapter()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        serviceBinder = ServiceBinder(LogcatService::class.java, this)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? =
            inflateLayout(R.layout.fragment_logcat_live)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView)
        linearLayoutManager = LinearLayoutManager(activity)
        recyclerView.layoutManager = linearLayoutManager
        recyclerView.itemAnimator = null
        recyclerView.addItemDecoration(DividerItemDecoration(activity,
                linearLayoutManager.orientation))
        recyclerView.adapter = adapter

        recyclerView.addOnScrollListener(onScrollListener)

        fabDown = view.findViewById(R.id.fabDown)
        fabDown.setOnClickListener {
            logcatService?.logcat?.pause()
            hideFabDown()
            ignoreScrollEvent = true
            viewModel.autoScroll = true
            linearLayoutManager.scrollToPosition(adapter.itemCount - 1)
            resumeLogcat()
        }

        fabUp = view.findViewById(R.id.fabUp)
        fabUp.setOnClickListener {
            logcatService?.logcat?.pause()
            hideFabUp()
            viewModel.autoScroll = false
            linearLayoutManager.scrollToPositionWithOffset(0, 0)
            resumeLogcat()
        }

        hideFabUp()
        hideFabDown()

        adapter.setOnClickListener { v ->
            val pos = linearLayoutManager.getPosition(v)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.logcat_live, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView

        var reachedBlank = false
        var lastLogId = -1
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String): Boolean {
                if (newText.isBlank()) {
                    reachedBlank = true
                    onSearchViewClose(lastLogId)
                } else {
                    reachedBlank = false
                    val logcat = logcatService?.logcat ?: return true
                    logcat.pause()
                    logcat.addFilter(FILTER_MSG, { log ->
                        log.tag.contains(newText) || log.msg.contains(newText)
                    })

                    adapter.clear()

                    val logs = logcat.getLogsFiltered()
                    if (logs.isNotEmpty()) {
                        lastLogId = logs[0].id
                    }

                    adapter.addItems(logs)

                    viewModel.autoScroll = false
                    linearLayoutManager.scrollToPositionWithOffset(0, 0)

                    resumeLogcat()
                }
                return true
            }

            override fun onQueryTextSubmit(query: String) = false
        })

        searchView.setOnCloseListener {
            if (!reachedBlank) {
                onSearchViewClose(lastLogId)
            }
            false
        }

        super.onCreateOptionsMenu(menu, inflater)
    }

    private fun onSearchViewClose(lastLogId: Int) {
        val logcat = logcatService?.logcat ?: return
        logcat.pause()
        logcat.clearFilters()

        adapter.clear()
        addAllLogs(logcat.getLogs())
        if (lastLogId == -1) {
            scrollRecyclerView()
        } else {
            viewModel.autoScroll = linearLayoutManager.findLastCompletelyVisibleItemPosition() ==
                    adapter.itemCount - 1
            if (!viewModel.autoScroll) {
                linearLayoutManager.scrollToPositionWithOffset(lastLogId, 0)
            }
        }

        resumeLogcat()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        // do nothing

        val pauseItem = menu.findItem(R.id.action_pause_logcat)
        val resumeItem = menu.findItem(R.id.action_resume_logcat)

        pauseItem.isVisible = !viewModel.paused
        resumeItem.isVisible = viewModel.paused
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                true
            }
            R.id.action_pause_logcat -> {
                viewModel.paused = true
                logcatService?.logcat?.pause()
                true
            }
            R.id.action_resume_logcat -> {
                viewModel.paused = false
                resumeLogcat()
                true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onStart() {
        super.onStart()
        serviceBinder.bind(activity)
    }

    override fun onStop() {
        super.onStop()
        serviceBinder.unbind(activity)
    }

    override fun onDestroy() {
        super.onDestroy()
        recyclerView.removeOnScrollListener(onScrollListener)
        logcatService?.logcat?.setEventListener(null)
        val logcat = logcatService?.logcat
        if (logcat != null) {
            (activity as AppCompatActivity).lifecycle.removeObserver(logcat)
        }
        serviceBinder.close()
    }

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        MyLogger.logDebug(LogcatLiveFragment::class, "onServiceConnected")
        logcatService = (service as LogcatService.LocalBinder).getLogcatService()

        if (adapter.itemCount > 0) {
            scrollRecyclerView()
            return
        }

        val logcat = logcatService!!.logcat
        logcat.pause()

        addAllLogs(logcat.getLogs())
        scrollRecyclerView()

        logcat.setEventListener(logcatEventListener)
        (activity as AppCompatActivity).lifecycle.addObserver(logcat)

        resumeLogcat()
    }

    private fun addAllLogs(logs: List<Log>) {
        adapter.addItems(logs)
        updateToolbarSubtitle(adapter.itemCount)
    }

    private fun scrollRecyclerView() {
        if (viewModel.autoScroll) {
            linearLayoutManager.scrollToPosition(adapter.itemCount - 1)
        } else {
            linearLayoutManager.scrollToPosition(viewModel.scrollPosition)
        }
    }

    private fun updateToolbarSubtitle(count: Int) {
        if (count > 1) {
            (activity as BaseActivity).toolbar.subtitle = "$count logs"
        } else {
            (activity as BaseActivity).toolbar.subtitle = null
        }
    }

    private fun resumeLogcat() {
        if (!viewModel.paused) {
            logcatService?.logcat?.resume()
        }
    }

    private fun updateUIOnLogEvent(count: Int) {
        updateToolbarSubtitle(count)
        if (viewModel.autoScroll) {
            linearLayoutManager.scrollToPosition(count - 1)
        }
    }

    override fun onServiceDisconnected(name: ComponentName) {
        MyLogger.logDebug(LogcatLiveFragment::class, "onServiceDisconnected")
        logcatService = null
    }

    companion object {
        val TAG = LogcatLiveFragment::class.qualifiedName
        private const val FILTER_MSG = "msg"
    }
}