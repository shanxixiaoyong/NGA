package gov.anzong.androidnga.activity.compose.topic

import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.justwen.androidnga.ui.compose.BaseComposeFragment
import gov.anzong.androidnga.activity.compose.board.ForumBoardViewModel
import sp.phone.param.ContentSource

class HiddenBoardsFragment : BaseComposeFragment() {
    private val ngaStore by lazy { TopicLocalState(ContentSource.NGA) }
    private val linuxDoStore by lazy { TopicLocalState(ContentSource.LINUX_DO) }
    private var boards by mutableStateOf(emptyList<HiddenBoard>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().title = "已屏蔽板块"
        reload()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        ngaStore.reloadHiddenState()
        linuxDoStore.reloadHiddenState()
        val ngaBoards = ngaStore.hiddenBoardEntries { fid ->
            ForumBoardViewModel.findBoard(fid)?.name?.takeIf(String::isNotBlank) ?: "板块 $fid"
        }
        val linuxDoBoards = linuxDoStore.hiddenBoardEntries { fid -> "LINUX DO 板块 $fid" }
        boards = ngaBoards + linuxDoBoards
    }

    @Composable
    override fun ContentView() {
        if (boards.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无已屏蔽板块")
            }
            return
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(boards, key = { "${it.source}_${it.fid}" }) { board ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(board.name, Modifier.weight(1f))
                    TextButton(onClick = {
                        if (board.source == ContentSource.LINUX_DO) {
                            linuxDoStore.unhideBoard(board.fid)
                        } else {
                            ngaStore.unhideBoard(board.fid)
                        }
                        reload()
                    }) {
                        Text("取消屏蔽")
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
