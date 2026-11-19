package com.xingheyuzhuan.shiguangschedule.ui.cumt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.xingheyuzhuan.shiguangschedule.Destination
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAccountProfile
import com.xingheyuzhuan.shiguangschedule.cumt.CumtConstants
import com.xingheyuzhuan.shiguangschedule.cumt.CumtLibrarySearchItem
import com.xingheyuzhuan.shiguangschedule.cumt.CumtLoanBook
import com.xingheyuzhuan.shiguangschedule.cumt.CumtLocalRepository
import com.xingheyuzhuan.shiguangschedule.cumt.CumtServiceRepository
import com.xingheyuzhuan.shiguangschedule.ui.components.ToastManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinViewModel
import shiguangschedule.shared.generated.resources.Res
import shiguangschedule.shared.generated.resources.chevron_right_24px
import shiguangschedule.shared.generated.resources.close_24px
import shiguangschedule.shared.generated.resources.menu_book_24px
import shiguangschedule.shared.generated.resources.refresh_24px
import shiguangschedule.shared.generated.resources.search_24px
import kotlin.time.Clock

@KoinViewModel
class CumtLibraryViewModel(
    private val localRepo: CumtLocalRepository,
    private val serviceRepo: CumtServiceRepository
) : ViewModel() {

    val profile: StateFlow<CumtAccountProfile> = localRepo.profileFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CumtAccountProfile()
    )

    private var jwtToken: String? = null

    private val _currentLoans = MutableStateFlow<List<CumtLoanBook>>(emptyList())
    val currentLoans: StateFlow<List<CumtLoanBook>> = _currentLoans.asStateFlow()

    private val _historyLoans = MutableStateFlow<List<CumtLoanBook>>(emptyList())
    val historyLoans: StateFlow<List<CumtLoanBook>> = _historyLoans.asStateFlow()

    private val _searchResults = MutableStateFlow<List<CumtLibrarySearchItem>>(emptyList())
    val searchResults: StateFlow<List<CumtLibrarySearchItem>> = _searchResults.asStateFlow()

    private val _isLoadingLoans = MutableStateFlow(false)
    val isLoadingLoans: StateFlow<Boolean> = _isLoadingLoans.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _loanError = MutableStateFlow<String?>(null)
    val loanError: StateFlow<String?> = _loanError.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    init {
        refreshLoans()
    }

    private suspend fun ensureToken(): Result<String> {
        jwtToken?.let { return Result.success(it) }
        val creds = localRepo.getSavedCredentials()
            ?: return Result.failure(IllegalStateException("请先绑定矿大统一身份认证后再查询借阅信息"))
        return serviceRepo.loginLibraryToken(creds.first, creds.second).onSuccess {
            jwtToken = it
        }
    }

    fun refreshLoans(forceRelogin: Boolean = false) {
        if (_isLoadingLoans.value) return
        viewModelScope.launch {
            _isLoadingLoans.value = true
            _loanError.value = null
            if (forceRelogin) jwtToken = null

            val tokenRes = ensureToken()
            val token = tokenRes.getOrElse {
                _loanError.value = it.message ?: "图书馆认证失败"
                _isLoadingLoans.value = false
                return@launch
            }

            val curRes = serviceRepo.fetchLibraryCurrentLoans(token)
            curRes.fold(
                onSuccess = { _currentLoans.value = it },
                onFailure = { e ->
                    if (!forceRelogin && e.message == "LIBRARY_TOKEN_EXPIRED") {
                        jwtToken = null
                        _isLoadingLoans.value = false
                        refreshLoans(forceRelogin = true)
                        return@launch
                    }
                    _loanError.value = e.message ?: "获取当前借阅失败"
                }
            )

            serviceRepo.fetchLibraryLoanHistory(token).onSuccess {
                _historyLoans.value = it
            }

            _isLoadingLoans.value = false
        }
    }

    fun renewSingle(book: CumtLoanBook) {
        viewModelScope.launch {
            val token = ensureToken().getOrElse {
                ToastManager.show(it.message ?: "请先登录")
                return@launch
            }
            serviceRepo.renewLibraryBook(token, book.loanId).fold(
                onSuccess = { msg ->
                    ToastManager.show("《${book.title}》：$msg")
                    refreshLoans()
                },
                onFailure = { ToastManager.show("《${book.title}》续借失败：${it.message}") }
            )
        }
    }

    fun renewAll() {
        val renewable = _currentLoans.value.filter { it.canRenew && it.loanId.isNotBlank() }
        if (renewable.isEmpty()) {
            ToastManager.show("暂无可续借图书")
            return
        }
        viewModelScope.launch {
            val token = ensureToken().getOrElse {
                ToastManager.show(it.message ?: "请先登录")
                return@launch
            }
            var okCount = 0
            for (b in renewable) {
                if (serviceRepo.renewLibraryBook(token, b.loanId).isSuccess) {
                    okCount++
                }
            }
            ToastManager.show("续借完成：$okCount / ${renewable.size} 本成功")
            refreshLoans()
        }
    }

    fun searchCatalog(keyword: String) {
        val clean = keyword.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            serviceRepo.searchLibraryCatalog(clean, page = 1, jwtToken = jwtToken).fold(
                onSuccess = {
                    _searchResults.value = it
                    if (it.isEmpty()) _searchError.value = "未检索到与「$clean」匹配的馆藏书目"
                },
                onFailure = { e ->
                    _searchError.value = "直连馆藏搜索暂不可用，可点击下方「图星图书馆官网检索」网页直查。\n详情：${e.message}"
                }
            )
            _isSearching.value = false
        }
    }
}

@Composable
fun CumtLibraryScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    viewModel: CumtLibraryViewModel = koinViewModel()
) {
    val profile by viewModel.profile.collectAsState()
    val currentLoans by viewModel.currentLoans.collectAsState()
    val historyLoans by viewModel.historyLoans.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoadingLoans by viewModel.isLoadingLoans.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val loanError by viewModel.loanError.collectAsState()
    val searchError by viewModel.searchError.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchKeyword by remember { mutableStateOf("") }
    val tabs = listOf("当前借阅", "历史借阅", "馆藏检索")

    val today = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }

    Scaffold(
        topBar = {
            CumtTopBar(
                title = "矿大图书馆",
                subtitle = "借阅管理 · 一键续借 · 馆藏查询",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { viewModel.refreshLoans(forceRelogin = true) }) {
                        if (isLoadingLoans) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(vectorResource(Res.drawable.refresh_24px), contentDescription = "刷新")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            CumtSegmentedBar(
                options = tabs,
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            when (selectedTab) {
                0, 1 -> {
                    if (profile.username.isBlank()) {
                        CumtEmptyState(
                            icon = Res.drawable.menu_book_24px,
                            accent = CumtAccent.TEAL,
                            title = "绑定统一身份认证后可查询借阅",
                            subtitle = "自动同步在借图书清单、到期倒计时，并支持一键续借",
                            primaryActionText = "去绑定账号",
                            onPrimaryAction = { onNavigate(Destination.CumtAuth) },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (isLoadingLoans && currentLoans.isEmpty() && historyLoans.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "正在同步馆藏借阅记录...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (loanError != null && currentLoans.isEmpty() && selectedTab == 0) {
                        CumtEmptyState(
                            icon = Res.drawable.menu_book_24px,
                            accent = CumtAccent.CORAL,
                            title = "借阅信息获取失败",
                            subtitle = loanError.orEmpty(),
                            primaryActionText = "重新登录并刷新",
                            onPrimaryAction = { viewModel.refreshLoans(forceRelogin = true) },
                            secondaryActionText = "网页版图书馆",
                            onSecondaryAction = {
                                onNavigate(Destination.WebView(CumtConstants.LIB_WEB_OPAC_URL, null))
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val books = if (selectedTab == 0) currentLoans else historyLoans
                        if (books.isEmpty()) {
                            CumtEmptyState(
                                icon = Res.drawable.menu_book_24px,
                                accent = CumtAccent.TEAL,
                                title = if (selectedTab == 0) "当前暂无在借图书" else "暂无历史借阅记录",
                                subtitle = if (selectedTab == 0) {
                                    "可切换到「馆藏检索」查找新书，或点击右上角刷新同步记录"
                                } else {
                                    "近期借还书记录将保存在此处方便回顾"
                                },
                                primaryActionText = if (selectedTab == 0) "检索馆藏新书" else null,
                                onPrimaryAction = if (selectedTab == 0) {
                                    { selectedTab = 2 }
                                } else null,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            val dueSoonCount = remember(currentLoans, today) {
                                currentLoans.count { book ->
                                    val d = runCatching { LocalDate.parse(book.normReturnDate.take(10)) }.getOrNull()
                                    d != null && today.daysUntil(d) <= 3
                                }
                            }
                            LazyColumn(
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (selectedTab == 0) {
                                    item {
                                        CumtHeroGradientCard(
                                            gradientColors = listOf(Color(0xFF0E7C86), Color(0xFF137E52)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "当前在借 ${currentLoans.size} 本",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                    Spacer(Modifier.height(2.dp))
                                                    Text(
                                                        text = if (dueSoonCount > 0) {
                                                            "其中 $dueSoonCount 本即将到期或已超期，建议及时续借"
                                                        } else {
                                                            "所有借阅状态正常，按时归还或一键续借"
                                                        },
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color.White.copy(alpha = 0.85f)
                                                    )
                                                }
                                                Spacer(Modifier.width(10.dp))
                                                Surface(
                                                    color = Color.White.copy(alpha = 0.22f),
                                                    shape = RoundedCornerShape(50),
                                                    onClick = viewModel::renewAll
                                                ) {
                                                    Text(
                                                        text = "一键续借全部",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                items(books, key = { "${it.loanId}_${it.title}_${it.loanDate}" }) { book ->
                                    LoanBookCard(
                                        book = book,
                                        isCurrent = selectedTab == 0,
                                        today = today,
                                        onRenew = { viewModel.renewSingle(book) }
                                    )
                                }
                            }
                        }
                    }
                }

                2 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchKeyword,
                                onValueChange = { searchKeyword = it },
                                placeholder = { Text("输入书名 / 作者 / ISBN") },
                                leadingIcon = {
                                    Icon(vectorResource(Res.drawable.search_24px), contentDescription = null)
                                },
                                trailingIcon = {
                                    if (searchKeyword.isNotEmpty()) {
                                        IconButton(onClick = { searchKeyword = "" }) {
                                            Icon(vectorResource(Res.drawable.close_24px), contentDescription = "清空")
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    viewModel.searchCatalog(searchKeyword)
                                }),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { viewModel.searchCatalog(searchKeyword) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.height(52.dp)
                            ) {
                                Text("搜索")
                            }
                        }

                        CumtSurfaceCard(
                            onClick = {
                                onNavigate(Destination.WebView(CumtConstants.LIB_WEB_OPAC_URL, null))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CumtIconBadge(
                                    icon = Res.drawable.menu_book_24px,
                                    accent = CumtAccent.TEAL,
                                    size = 38.dp,
                                    iconSize = 20.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "图星 OPAC 官网检索",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "直达矿大图书馆网页查看详细馆藏楼层与复本",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    vectorResource(Res.drawable.chevron_right_24px),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isSearching) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (searchError != null && searchResults.isEmpty()) {
                            CumtEmptyState(
                                icon = Res.drawable.search_24px,
                                accent = CumtAccent.OCEAN,
                                title = "未直连检索到馆藏结果",
                                subtitle = searchError.orEmpty(),
                                primaryActionText = "打开图星图书馆官网检索",
                                onPrimaryAction = {
                                    onNavigate(Destination.WebView(CumtConstants.LIB_WEB_OPAC_URL, null))
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (searchResults.isEmpty()) {
                            CumtEmptyState(
                                icon = Res.drawable.search_24px,
                                accent = CumtAccent.TEAL,
                                title = "搜索矿大图星馆藏",
                                subtitle = "输入关键词查找教材教参、计算机与专业书籍，或直接点击上方卡片前往官网检索",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(searchResults, key = { "${it.recordId}_${it.title}_${it.callNumber}" }) { item ->
                                    LibrarySearchCard(
                                        item = item,
                                        onOpenDetail = {
                                            onNavigate(Destination.WebView(CumtConstants.LIB_WEB_OPAC_URL, null))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoanBookCard(
    book: CumtLoanBook,
    isCurrent: Boolean,
    today: LocalDate,
    onRenew: () -> Unit
) {
    val returnDate = remember(book.normReturnDate) {
        val part = book.normReturnDate.take(10)
        runCatching { LocalDate.parse(part) }.getOrNull()
    }
    val daysRemaining = returnDate?.let { today.daysUntil(it) }
    val urgencyAccent = when {
        daysRemaining == null -> CumtAccent.SLATE
        daysRemaining <= 3 -> CumtAccent.CORAL
        daysRemaining <= 7 -> CumtAccent.AMBER
        else -> CumtAccent.EMERALD
    }

    CumtSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (!book.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = book.coverUrl,
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 58.dp, height = 82.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                val spineColors = if ((book.title.hashCode() and 1) == 0) {
                    listOf(Color(0xFF0E7C86), Color(0xFF137E52))
                } else {
                    listOf(Color(0xFF2843A8), Color(0xFF0A6CA8))
                }
                val initial = book.title.trim().firstOrNull { !it.isWhitespace() }?.toString() ?: "书"
                Box(
                    modifier = Modifier
                        .size(width = 58.dp, height = 82.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.verticalGradient(spineColors))
                ) {
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .fillMaxHeight()
                            .background(Color.White.copy(alpha = 0.22f))
                    )
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (book.author.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CumtStatusPill(
                        text = book.locationName.ifBlank { "馆藏地未知" },
                        containerColor = CumtAccent.TEAL.containerColor(),
                        contentColor = CumtAccent.TEAL.contentColor()
                    )
                    if (book.callNumber.isNotBlank()) {
                        CumtStatusPill(
                            text = book.callNumber,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "借阅 ${book.loanDate.take(10)} → 应还 ${book.normReturnDate.take(10)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isCurrent) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val statusText = when {
                            daysRemaining == null -> "已续借 ${book.renewCount} 次"
                            daysRemaining < 0 -> "已超期 ${-daysRemaining} 天"
                            daysRemaining == 0 -> "今日到期"
                            else -> "剩余 $daysRemaining 天"
                        }
                        CumtStatusPill(
                            text = statusText,
                            containerColor = urgencyAccent.containerColor(),
                            contentColor = urgencyAccent.contentColor(),
                            dotColor = urgencyAccent.contentColor()
                        )
                        if (book.canRenew && book.loanId.isNotBlank()) {
                            FilledTonalButton(
                                onClick = onRenew,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("续借 (${book.renewCount}/2)", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySearchCard(
    item: CumtLibrarySearchItem,
    onOpenDetail: () -> Unit
) {
    CumtSurfaceCard(
        onClick = onOpenDetail,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = listOf(item.author, item.publisher, item.publishYear)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { "矿大馆藏文献" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CumtStatusPill(
                        text = "索书号 ${item.callNumber.ifBlank { "--" }}",
                        containerColor = CumtAccent.TEAL.containerColor(),
                        contentColor = CumtAccent.TEAL.contentColor()
                    )
                    if (item.availableCount != null && item.totalCount != null) {
                        val hasAvailable = item.availableCount > 0
                        val availAccent = if (hasAvailable) CumtAccent.EMERALD else CumtAccent.SLATE
                        CumtStatusPill(
                            text = "可借 ${item.availableCount}/${item.totalCount}",
                            containerColor = availAccent.containerColor(),
                            contentColor = availAccent.contentColor()
                        )
                    }
                }
                Icon(
                    vectorResource(Res.drawable.chevron_right_24px),
                    contentDescription = "馆藏详情",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
