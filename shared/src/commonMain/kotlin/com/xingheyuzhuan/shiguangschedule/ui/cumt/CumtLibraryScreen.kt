package com.xingheyuzhuan.shiguangschedule.ui.cumt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import shiguangschedule.shared.generated.resources.arrow_back_24px
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

@OptIn(ExperimentalMaterial3Api::class)
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
            TopAppBar(
                title = { Text("矿大图书馆") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(vectorResource(Res.drawable.arrow_back_24px), contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshLoans(forceRelogin = true) }) {
                        Icon(vectorResource(Res.drawable.refresh_24px), contentDescription = "刷新")
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
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0, 1 -> {
                    if (profile.username.isBlank()) {
                        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("绑定统一身份认证后可查询借阅与续借", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = { onNavigate(Destination.CumtAuth) }) {
                                    Text("去绑定账号")
                                }
                            }
                        }
                    } else if (isLoadingLoans) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (loanError != null && currentLoans.isEmpty() && selectedTab == 0) {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(loanError.orEmpty(), color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { viewModel.refreshLoans(forceRelogin = true) }) {
                                        Text("重新登录并刷新")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            onNavigate(Destination.WebView(CumtConstants.LIB_WEB_OPAC_URL, null))
                                        }
                                    ) { Text("网页版图书馆") }
                                }
                            }
                        }
                    } else {
                        val books = if (selectedTab == 0) currentLoans else historyLoans
                        if (books.isEmpty()) {
                            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (selectedTab == 0) "当前暂无在借图书" else "暂无历史借阅记录",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (selectedTab == 0) {
                                    item {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("在借共 ${currentLoans.size} 本", fontWeight = FontWeight.SemiBold)
                                            OutlinedButton(onClick = viewModel::renewAll) {
                                                Text("一键续借全部")
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
                                .padding(16.dp),
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
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    viewModel.searchCatalog(searchKeyword)
                                }),
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = { viewModel.searchCatalog(searchKeyword) }) {
                                Text("搜索")
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "矿大图星馆藏查询",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = {
                                    onNavigate(Destination.WebView(CumtConstants.LIB_WEB_OPAC_URL, null))
                                }
                            ) {
                                Text("在图星 OPAC 官网检索 →")
                            }
                        }

                        if (isSearching) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (searchError != null && searchResults.isEmpty()) {
                            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(searchError.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            onNavigate(Destination.WebView(CumtConstants.LIB_WEB_OPAC_URL, null))
                                        }
                                    ) {
                                        Text("打开图星图书馆官网检索")
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
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

    Card(modifier = Modifier.fillMaxWidth()) {
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
                        .size(width = 56.dp, height = 78.dp)
                        .clip(MaterialTheme.shapes.small)
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(width = 56.dp, height = 78.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            vectorResource(Res.drawable.menu_book_24px),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
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
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "馆藏: ${book.locationName.ifBlank { "--" }} · 索书号 ${book.callNumber.ifBlank { "--" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "借阅 ${book.loanDate.take(10)} → 应还 ${book.normReturnDate.take(10)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isCurrent) {
                    Spacer(Modifier.height(6.dp))
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
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (daysRemaining != null && daysRemaining <= 3) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                        if (book.canRenew && book.loanId.isNotBlank()) {
                            OutlinedButton(onClick = onRenew) {
                                Text("续借 (${book.renewCount}/2)")
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = listOf(item.author, item.publisher, item.publishYear)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { "矿大馆藏文献" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "索书号: ${item.callNumber.ifBlank { "--" }}" +
                        if (item.availableCount != null && item.totalCount != null) {
                            " · 可借 ${item.availableCount}/${item.totalCount}"
                        } else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = onOpenDetail) {
                    Text("馆藏详情")
                }
            }
        }
    }
}
