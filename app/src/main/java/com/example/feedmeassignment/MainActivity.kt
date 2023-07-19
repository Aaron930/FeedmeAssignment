package com.example.feedmeassignment

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.feedmeassignment.model.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"
const val ORDER_PROCESS_DURATION: Long = 3_000

class MainActivity : ComponentActivity() {

    data class OrderState(
        val numOfBot: Int = 0,
        val orderInitID: Int = 0,
    )

    class OrderViewModel : ViewModel() {
        var isProcessOrdersExecuted = false
        private val cookingBotStack = mutableListOf<CookingBot>()
        private val orderChannel = Channel<Order>()

        private val _uiState = mutableStateOf(OrderState())
        val uiState: State<OrderState> get() = _uiState

        private val _orderList: MutableLiveData<MutableList<Order>> = MutableLiveData()
        val orderList: LiveData<MutableList<Order>> = _orderList

        private val _completeList: MutableLiveData<MutableList<Order>> = MutableLiveData()
        val completeList: LiveData<MutableList<Order>> = _completeList

        init {
            _orderList.value = mutableListOf()
            _completeList.value = mutableListOf()
        }

        fun increaseOrderID() {
            val newUiState = uiState.value.copy(
                orderInitID = uiState.value.orderInitID + 1
            )
            _uiState.value = newUiState
        }

        fun addOrder(newOrder: Order) {
            _orderList.value?.add(newOrder)
            _orderList.value?.sortWith(orderSortComparator)
        }

        fun increaseBot() {
            val newUiState = uiState.value.copy(
                numOfBot = uiState.value.numOfBot + 1
            )
            _uiState.value = newUiState

            val cookingBot = CookingBot()
            cookingBotStack.add(cookingBot)
            launchCookingBot(cookingBot)
        }

        fun decreaseBot() {
            val newUiState = uiState.value.copy(
                numOfBot = uiState.value.numOfBot - 1
            )
            _uiState.value = newUiState

            if (cookingBotStack.isNotEmpty()) {
                val cookingBot = cookingBotStack.removeAt(cookingBotStack.size - 1)
                cookingBot.stop()
            }
        }

        private fun launchCookingBot(cookingBot: CookingBot) {
            cookingBot.start()
        }

        inner class CookingBot() {
            private var isActive = true
            private var job: Job? = null

            fun start() {
                isActive = true
                job = viewModelScope.launch {
                    for (order in orderChannel) {
                        if (!isActive)
                            break

                        try {
                            delay(ORDER_PROCESS_DURATION)

                            order.isComplete = true
                            order.isProcessing = false

                            _completeList.value?.add(order)
                            _orderList.value?.remove(order)
                        } catch (e: CancellationException) {
                            order.isProcessing = false
                        } catch (e: Exception) {
                            throw IllegalArgumentException(e)
                        }
                    }
                }
            }

            fun stop() {
                isActive = false
                job?.cancel()
                job = null
            }
        }

        fun processOrders() {
            repeat(cookingBotStack.size) {
                val cookingBot = CookingBot()
                cookingBotStack.add(cookingBot)
                launchCookingBot(cookingBot)
            }
        }

        suspend fun startProcess() {
            while (true) {
                val orders = _orderList.value ?: mutableListOf()
                if (orders.isNotEmpty()) {
                    orders.forEach() { order ->
                        if (!order.isComplete && !order.isProcessing) {
                            if (enqueueOrder(order)) {
                                order.isProcessing = true
                            }
                        }
                    }
                }
                delay(1000)
            }
        }

        private suspend fun enqueueOrder(order: Order): Boolean {
            val result = CompletableDeferred<Boolean>()
            viewModelScope.launch {
                result.complete(orderChannel.trySend(order).isSuccess)
            }
            return result.await()
        }

        private val orderSortComparator = Comparator<Order> { item1, item2 ->
            if (item1.isVip && !item2.isVip) {
                -1
            } else if (!item1.isVip && item2.isVip) {
                1
            } else {
                0
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                color = MaterialTheme.colorScheme.background
            ) {
                Main()
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun PreviewMain() {
        Main()
    }

    @Composable
    fun Main() {
        val viewModel: OrderViewModel = viewModel()
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            if (!viewModel.isProcessOrdersExecuted) {
                viewModel.isProcessOrdersExecuted = true
                scope.launch {
                    viewModel.processOrders()
                    viewModel.startProcess()
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            val sortedItems by viewModel.orderList.observeAsState(emptyList())
            val completeItems by viewModel.completeList.observeAsState(emptyList())
            val lazyPendingListState = rememberLazyListState()
            val lazyCompleteListState = rememberLazyListState()

            Row(
                Modifier
                    .height(400.dp)
            ) {
                PendingOrderView(
                    sortedItems.let { it.filter { !it.isComplete } },
                    lazyPendingListState,
                    Modifier
                        .weight(1f)
                )
                CompleteOrderView(
                    completeItems.let { it.filter { it.isComplete } },
                    lazyCompleteListState,
                    Modifier
                        .weight(1f)
                )
            }
            OperatingArea(
                onNewOrder = { newOrder ->
                    viewModel.increaseOrderID()
                    viewModel.addOrder(newOrder = newOrder)

                    scope.launch {
                        lazyPendingListState.animateScrollToItem(0)
                    }
                },
                viewModel
            )
            Text(
                text = "Number of Cooking Bot: ${viewModel.uiState.value.numOfBot}",
                Modifier.padding(16.dp)
            )
        }
    }

    @Composable
    fun PendingOrderView(orders: List<Order>, lazyListState: LazyListState, modifier: Modifier) {
        Column(
            modifier = modifier
        ) {
            Text(
                text = "Pending ",
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
            )
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.LightGray)
            ) {
                items(orders) { message ->
                    OrderItem(message)
                }
            }
        }
    }

    @Composable
    fun CompleteOrderView(orders: List<Order>, lazyListState: LazyListState, modifier: Modifier) {
        LaunchedEffect(orders) {
            if (orders.isNotEmpty()) {
                lazyListState.animateScrollToItem(orders.lastIndex)
            }
        }

        Column(
            modifier = modifier
        ) {
            Text(
                text = "Complete",
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
            )
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.LightGray),
            ) {
                items(orders) { message ->
                    OrderItem(message)
                }
            }
        }
    }

    @Composable
    fun OrderItem(order: Order) {
        Row(Modifier.padding(8.dp)) {
            Text(
                text = " ${order.ID} : ${order.content}",
                modifier = Modifier.padding(end = 8.dp),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
    }

    @Composable
    fun OperatingArea(onNewOrder: (Order) -> Unit, viewModel: OrderViewModel) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    modifier = Modifier.width(170.dp),
                    onClick = {
                        onNewOrder(
                            Order(
                                viewModel.uiState.value.orderInitID,
                                false,
                                "This is new order"
                            )
                        )
                    }) {
                    Text(text = "New Normal Order")
                }
                Button(
                    modifier = Modifier.width(170.dp),
                    onClick = {
                        onNewOrder(
                            Order(
                                viewModel.uiState.value.orderInitID,
                                true,
                                "This is Vip order"
                            )
                        )
                    }) {
                    Text(text = "New VIP Order")
                }

            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    modifier = Modifier.width(170.dp),
                    onClick = {
                        viewModel.increaseBot()
                    }) {
                    Text(text = "+ Bot")
                }
                Button(
                    modifier = Modifier.width(170.dp),
                    onClick = {
                        viewModel.decreaseBot()
                    }) {
                    Text(text = "- Bot")
                }

            }
        }

    }
}