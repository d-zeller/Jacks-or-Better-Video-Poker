package com.jacksorbetter.tv

import kotlinx.coroutines.delay
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import android.media.MediaPlayer

class MainActivity : ComponentActivity() {
    private var backgroundPlayer: MediaPlayer? = null
    val isMuted = mutableStateOf(false) // Made public so composable can access

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        backgroundPlayer = MediaPlayer.create(this, R.raw.bg_music)
        backgroundPlayer?.isLooping = true
        backgroundPlayer?.start()

        setContent {
            MaterialTheme {
                val muted by isMuted

                // This effect watches isMuted and pauses/starts background music accordingly
                LaunchedEffect(muted) {
                    if (muted) {
                        backgroundPlayer?.pause()
                    } else {
                        backgroundPlayer?.start()
                    }
                }

                PokerGameScreen(isMuted = isMuted)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        backgroundPlayer?.pause()
    }

    override fun onResume() {
        super.onResume()
        if (!isMuted.value) {
            backgroundPlayer?.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundPlayer?.release()
        backgroundPlayer = null
    }
}

data class Card(val rank: String, val suit: String)

val ranks = listOf("2", "3", "4", "5", "6", "7", "8", "9", "10", "jack", "queen", "king", "ace")
val suits = listOf("spades", "hearts", "diamonds", "clubs")
fun getDeck(): List<Card> = suits.flatMap { s -> ranks.map { r -> Card(r, s) } }

fun cardToResourceName(card: Card): String {
    val rankMap = mapOf(
        "2" to "two",
        "3" to "three",
        "4" to "four",
        "5" to "five",
        "6" to "six",
        "7" to "seven",
        "8" to "eight",
        "9" to "nine",
        "10" to "ten",
        "jack" to "jack",
        "queen" to "queen",
        "king" to "king",
        "ace" to "ace"
    )
    val rankStr = rankMap[card.rank.lowercase()] ?: card.rank.lowercase()
    val suitStr = card.suit.lowercase()
    return "${rankStr}_of_${suitStr}"
}

@Composable
fun PokerGameScreen(isMuted: MutableState<Boolean>) {
    var deck by remember { mutableStateOf(getDeck().shuffled()) }
    var hand by remember { mutableStateOf(listOf<Card>()) }
    var held by remember { mutableStateOf(List(5) { false }) }
    var credits by remember { mutableStateOf(100) }
    var bet by remember { mutableStateOf(1) }
    var message by remember { mutableStateOf("") }
    var gameState by remember { mutableStateOf("ready") }
    var showCardFronts by remember { mutableStateOf(false) }
    var pendingFlip by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scrollState = rememberScrollState()

    LaunchedEffect(pendingFlip) {
        if (pendingFlip) {
            delay(250)
            showCardFronts = true
            pendingFlip = false
        }
    }

    fun deal() {
        if (credits < bet) {
            gameState = "gameover"
            message = "💰 Game Over"
            return
        }
        deck = getDeck().shuffled()
        hand = deck.take(5)
        held = List(5) { false }
        credits -= bet
        message = ""
        gameState = "dealt"
        showCardFronts = false
        pendingFlip = true
    }

    fun draw() {
        val newCards = deck.drop(5).take(5)
        hand = hand.mapIndexed { i, card -> if (held[i]) card else newCards.getOrNull(i) ?: card }
        val result = evaluateHand(hand)
        val payout = calculatePayout(result, bet)
        credits += payout

        if (payout > 0) {
            message = "$result! +$payout"

            // Play win sound only if not muted
            if (!isMuted.value) {
                val mediaPlayer = MediaPlayer.create(context, R.raw.winfx)
                mediaPlayer.setVolume(1f, 1f) // Full volume
                mediaPlayer.setOnCompletionListener {
                    it.release()
                }
                mediaPlayer.start()
            }
        } else {
            message = "💰 Game Over"
        }

        gameState = if (credits == 0) "gameover" else "ready"
        showCardFronts = true
    }

    fun resetGame() {
        credits = 100
        bet = 1
        hand = listOf()
        held = List(5) { false }
        message = ""
        gameState = "ready"
    }

    // UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "JACKS OR BETTER VIDEO POKER",
                    color = Color.Yellow,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                IconButton(onClick = { isMuted.value = !isMuted.value }) {
                    val iconRes = if (isMuted.value) android.R.drawable.ic_lock_silent_mode else android.R.drawable.ic_lock_silent_mode_off
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = if (isMuted.value) "Unmute" else "Mute",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            PayTable(bet = bet)
            Spacer(modifier = Modifier.height(8.dp))

            // CARDS ROW
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                hand.forEachIndexed { i, card ->
                    Column(
                        modifier = Modifier
                            .padding(4.dp)
                            .width(80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (held[i]) {
                            Text(
                                "HELD",
                                color = Color.Green,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(18.dp))
                        }

                        Box(
                            modifier = Modifier
                                .size(80.dp, 120.dp)
                                .border(2.dp, if (held[i]) Color.Green else Color.White)
                                .background(Color.DarkGray)
                                .clickable(enabled = gameState == "dealt") {
                                    held = held.toMutableList().also { it[i] = !it[i] }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val resId = if (gameState == "dealt" && !showCardFronts) {
                                context.resources.getIdentifier("card_back", "drawable", context.packageName)
                            } else {
                                val resName = cardToResourceName(card)
                                context.resources.getIdentifier(resName, "drawable", context.packageName)
                            }

                            if (resId != 0) {
                                Image(
                                    painter = painterResource(id = resId),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    "${card.rank.uppercase()}\n${card.suit.uppercase()}",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // BETTING
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        bet = when {
                            bet >= 5 || bet >= credits -> 1
                            else -> bet + 1
                        }
                    },
                    enabled = gameState != "dealt" && gameState != "gameover"
                ) {
                    Text("Bet One")
                }

                Text("Bet: $bet", color = Color.Cyan, fontSize = 18.sp)

                Button(
                    onClick = {
                        bet = if (credits >= 5) 5 else credits
                        deal()
                    },
                    enabled = gameState != "dealt" && gameState != "gameover" && credits > 0
                ) {
                    Text("Max Bet")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    when (gameState) {
                        "ready" -> deal()
                        "dealt" -> draw()
                        "gameover" -> resetGame()
                    }
                },
                modifier = Modifier
                    .wrapContentWidth()
                    .height(48.dp)
            ) {
                Text(
                    when (gameState) {
                        "ready" -> "Deal"
                        "dealt" -> "Draw"
                        "gameover" -> "Try Again"
                        else -> ""
                    },
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(100.dp))
        }

        // BOTTOM HUD
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xAA000000))
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Credits: $credits", color = Color.Green, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                message,
                color = if (gameState == "gameover") Color.Red else Color.Magenta,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
                textAlign = TextAlign.Center
            )
        }
        Text(
            "Programmed by Don Zeller",
            color = Color(0xFF00FF00),
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 4.dp)
        )
    }
    }

@Composable
fun PayTable(bet: Int) {
    val headers = listOf("Hand", "1", "2", "3", "4", "5")
    val payTableData = listOf(
        "Royal Flush" to listOf(250, 500, 750, 1000, 4000),
        "Straight Flush" to listOf(50, 100, 150, 200, 250),
        "Four of a Kind" to listOf(25, 50, 75, 100, 125),
        "Full House" to listOf(9, 18, 27, 36, 45),
        "Flush" to listOf(6, 12, 18, 24, 30),
        "Straight" to listOf(4, 8, 12, 16, 20),
        "Three of a Kind" to listOf(3, 6, 9, 12, 15),
        "Two Pair" to listOf(2, 4, 6, 8, 10),
        "Jacks or Better" to listOf(1, 2, 3, 4, 5)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            headers.forEachIndexed { i, header ->
                Text(
                    header,
                    color = Color.Yellow,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(if (i == 0) 2.5f else 1f),
                    style = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        payTableData.forEach { (handName, payouts) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    handName,
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(2.5f)
                )
                payouts.forEachIndexed { index, payout ->
                    val isCurrentBet = (index + 1 == bet)
                    Text(
                        payout.toString(),
                        color = if (isCurrentBet) Color.Green else Color.LightGray,
                        fontSize = 10.sp,
                        fontWeight = if (isCurrentBet) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                        style = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center)
                    )
                }
            }
        }
    }
}

fun evaluateHand(hand: List<Card>): String {
    val rankOrder = listOf("2", "3", "4", "5", "6", "7", "8", "9", "10", "jack", "queen", "king", "ace")
    val rankCounts = hand.groupingBy { it.rank }.eachCount()
    val suits = hand.map { it.suit }
    val flush = suits.distinct().size == 1
    val indices = hand.map { rankOrder.indexOf(it.rank) }.sorted()
    val straight = indices.zipWithNext().all { (a, b) -> b - a == 1 } || indices == listOf(0, 1, 2, 3, 12)
    val royal = hand.map { it.rank }.toSet() == setOf("10", "jack", "queen", "king", "ace")

    return when {
        flush && royal -> "Royal Flush"
        flush && straight -> "Straight Flush"
        rankCounts.containsValue(4) -> "Four of a Kind"
        rankCounts.containsValue(3) && rankCounts.containsValue(2) -> "Full House"
        flush -> "Flush"
        straight -> "Straight"
        rankCounts.containsValue(3) -> "Three of a Kind"
        rankCounts.filterValues { it == 2 }.size == 2 -> "Two Pair"
        rankCounts.filterValues { it == 2 }.keys.any { it in listOf("jack", "queen", "king", "ace") } -> "Jacks or Better"
        else -> "No Win"
    }
}

fun calculatePayout(handName: String, bet: Int): Int {
    return when (handName) {
        "Royal Flush" -> if (bet == 5) 4000 else 250 * bet
        "Straight Flush" -> 50 * bet
        "Four of a Kind" -> 25 * bet
        "Full House" -> 9 * bet
        "Flush" -> 6 * bet
        "Straight" -> 4 * bet
        "Three of a Kind" -> 3 * bet
        "Two Pair" -> 2 * bet
        "Jacks or Better" -> 1 * bet
        else -> 0
    }
}
