package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.R
import com.example.model.MarketItem
import com.example.ui.theme.ConnectNpBlue
import com.example.ui.theme.ConnectNpSuccess
import com.example.ui.theme.NepalCrimson

enum class MarketplaceSection {
    EXPLORE,
    MY_LISTINGS
}

enum class PriceFilter(val labelEn: String, val labelNp: String) {
    ALL("All Prices", "सबै मूल्य"),
    UNDER_5K("Under रु 5K", "रु ५ हजार मुनि"),
    FROM_5K_TO_20K("रु 5K - 20K", "रु ५K - २०K"),
    FROM_20K_TO_100K("रु 20K - 1 Lakh", "रु २०K - १ लाख"),
    OVER_100K("Over रु 1 Lakh", "रु १ लाख माथि")
}

enum class SortOption(val labelEn: String, val labelNp: String) {
    FEATURED("Featured", "विशेष"),
    PRICE_LOW_HIGH("Price: Low to High", "मूल्य: कम देखि धेरै"),
    PRICE_HIGH_LOW("Price: High to Low", "मूल्य: धेरै देखि कम"),
    NEWEST("Newest First", "नयाँ पहिले")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceScreen(
    items: List<MarketItem>,
    isNepali: Boolean,
    currentUserId: String = "user_me",
    onAddListing: (title: String, price: Long, category: String, location: String, description: String, mediaUri: Uri?) -> Unit,
    onBuyEscrow: (MarketItem) -> Unit,
    onMessageSeller: ((MarketItem) -> Unit)? = null,
    onUpdatePrice: ((itemId: String, newPrice: Long) -> Unit)? = null,
    onToggleSoldStatus: ((itemId: String) -> Unit)? = null,
    onDeleteListing: ((itemId: String) -> Unit)? = null
) {
    var activeSection by remember { mutableStateOf(MarketplaceSection.EXPLORE) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedPriceFilter by remember { mutableStateOf(PriceFilter.ALL) }
    var selectedSortOption by remember { mutableStateOf(SortOption.FEATURED) }
    var showFilterSheet by remember { mutableStateOf(false) }

    var selectedItemForDetail by remember { mutableStateOf<MarketItem?>(null) }
    var showSellDialog by remember { mutableStateOf(false) }

    // Dialog state for editing price in My Listings
    var itemToEditPrice by remember { mutableStateOf<MarketItem?>(null) }
    // Dialog state for confirming delete in My Listings
    var itemToDelete by remember { mutableStateOf<MarketItem?>(null) }

    val categories = listOf("All", "Handicrafts", "Fashion", "Electronics", "Vehicles", "Property", "Home & Living")

    // Filter user listings vs all listings
    val myListings = remember(items, currentUserId) {
        items.filter { it.seller.id == currentUserId || it.seller.name == "ConnectNP Member" }
    }

    // Filter and sort items for Explore tab
    val filteredItems = remember(items, searchQuery, selectedCategory, selectedPriceFilter, selectedSortOption) {
        var result = items.asSequence()

        // 1. Search Query filter
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            result = result.filter { item ->
                item.title.lowercase().contains(q) ||
                item.description.lowercase().contains(q) ||
                item.location.lowercase().contains(q) ||
                item.category.lowercase().contains(q) ||
                item.seller.name.lowercase().contains(q)
            }
        }

        // 2. Category filter
        if (selectedCategory != "All") {
            result = result.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }

        // 3. Price range filter
        result = when (selectedPriceFilter) {
            PriceFilter.ALL -> result
            PriceFilter.UNDER_5K -> result.filter { it.priceNpr < 5000L }
            PriceFilter.FROM_5K_TO_20K -> result.filter { it.priceNpr in 5000L..20000L }
            PriceFilter.FROM_20K_TO_100K -> result.filter { it.priceNpr in 20001L..100000L }
            PriceFilter.OVER_100K -> result.filter { it.priceNpr > 100000L }
        }

        // 4. Sorting
        val list = result.toList()
        when (selectedSortOption) {
            SortOption.FEATURED -> list.sortedWith(compareByDescending<MarketItem> { it.isFeatured }.thenBy { it.isSold })
            SortOption.PRICE_LOW_HIGH -> list.sortedBy { it.priceNpr }
            SortOption.PRICE_HIGH_LOW -> list.sortedByDescending { it.priceNpr }
            SortOption.NEWEST -> list.sortedByDescending { it.createdAt }
        }
    }

    val activeFilterCount = (if (selectedCategory != "All") 1 else 0) +
            (if (selectedPriceFilter != PriceFilter.ALL) 1 else 0) +
            (if (selectedSortOption != SortOption.FEATURED) 1 else 0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("marketplace_screen")
    ) {
        // Top Action Bar with Logo & Sell button
        Surface(
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isNepali) "CONNECTNP बजार" else "CONNECTNP MARKETPLACE",
                            fontFamily = com.example.ui.theme.PoppinsFontFamily,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            letterSpacing = 0.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = NepalCrimson,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "🇳🇵 Nepal Nationwide • Verified P2P",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // "Sell Item" button
                    Button(
                        onClick = { showSellDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = NepalCrimson),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("sell_item_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isNepali) "+ सामान बेच्नुहोस्" else "+ Post Item",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Section Tabs: Explore vs My Listings
                TabRow(
                    selectedTabIndex = if (activeSection == MarketplaceSection.EXPLORE) 0 else 1,
                    containerColor = Color.Transparent,
                    contentColor = ConnectNpBlue,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = activeSection == MarketplaceSection.EXPLORE,
                        onClick = { activeSection = MarketplaceSection.EXPLORE },
                        text = {
                            Text(
                                text = if (isNepali) "बजार हेर्नुहोस्" else "Explore Marketplace",
                                fontWeight = if (activeSection == MarketplaceSection.EXPLORE) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        },
                        icon = { Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("tab_explore_marketplace")
                    )

                    Tab(
                        selected = activeSection == MarketplaceSection.MY_LISTINGS,
                        onClick = { activeSection = MarketplaceSection.MY_LISTINGS },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isNepali) "मेरो सामान" else "My Listings",
                                    fontWeight = if (activeSection == MarketplaceSection.MY_LISTINGS) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge(containerColor = if (activeSection == MarketplaceSection.MY_LISTINGS) NepalCrimson else MaterialTheme.colorScheme.surfaceVariant) {
                                    Text("${myListings.size}", fontSize = 10.sp)
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("tab_my_listings")
                    )
                }
            }
        }

        // Section Content
        when (activeSection) {
            MarketplaceSection.EXPLORE -> {
                // Search Bar and Filters
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Search Input with leading search icon and trailing clear/filter buttons
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("marketplace_search_input"),
                        placeholder = {
                            Text(
                                text = if (isNepali) "सामान, वर्ग वा स्थान खोज्नुहोस्..." else "Search items, category, location...",
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = ConnectNpBlue)
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                                    }
                                }
                                IconButton(
                                    onClick = { showFilterSheet = true },
                                    modifier = Modifier.testTag("marketplace_filter_button")
                                ) {
                                    BadgedBox(badge = {
                                        if (activeFilterCount > 0) {
                                            Badge { Text("$activeFilterCount") }
                                        }
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = "Filters",
                                            tint = if (activeFilterCount > 0) NepalCrimson else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ConnectNpBlue,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Categories Horizontal Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categories) { cat ->
                            val isSelected = selectedCategory == cat
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat, fontSize = 12.sp) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Price Range Quick Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(PriceFilter.values()) { pf ->
                            val isSelected = selectedPriceFilter == pf
                            SuggestionChip(
                                onClick = { selectedPriceFilter = pf },
                                label = {
                                    Text(
                                        text = if (isNepali) pf.labelNp else pf.labelEn,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) NepalCrimson else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                border = SuggestionChipDefaults.suggestionChipBorder(
                                    enabled = true,
                                    borderColor = if (isSelected) NepalCrimson else MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                        }
                    }
                }

                // Active Filters Summary / Reset bar
                if (activeFilterCount > 0 || searchQuery.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${filteredItems.size} ${if (isNepali) "सामान भेटियो" else "items found"}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                searchQuery = ""
                                selectedCategory = "All"
                                selectedPriceFilter = PriceFilter.ALL
                                selectedSortOption = SortOption.FEATURED
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(if (isNepali) "फिल्टर हटाउनुहोस्" else "Reset Filters", fontSize = 11.sp)
                        }
                    }
                }

                // Product Grid or Empty State
                if (filteredItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (isNepali) "कुनै सामान भेटिएन" else "No Items Found",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isNepali) "कृपया फरक खोज शब्द वा फिल्टर प्रयास गर्नुहोस्।" else "Try adjusting your search keywords or price filters.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    searchQuery = ""
                                    selectedCategory = "All"
                                    selectedPriceFilter = PriceFilter.ALL
                                }
                            ) {
                                Text(if (isNepali) "सबै सामान देखाउनुहोस्" else "Show All Items")
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredItems, key = { it.id }) { item ->
                            ProductCard(
                                item = item,
                                onClick = { selectedItemForDetail = item }
                            )
                        }
                    }
                }
            }

            MarketplaceSection.MY_LISTINGS -> {
                // My Listings View
                MyListingsView(
                    myListings = myListings,
                    isNepali = isNepali,
                    onAddNewListing = { showSellDialog = true },
                    onItemClick = { item -> selectedItemForDetail = item },
                    onEditPrice = { item -> itemToEditPrice = item },
                    onToggleSold = { item -> onToggleSoldStatus?.invoke(item.id) },
                    onDelete = { item -> itemToDelete = item }
                )
            }
        }
    }

    // Filter Options Bottom Sheet
    if (showFilterSheet) {
        ModalBottomSheet(onDismissRequest = { showFilterSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isNepali) "फिल्टर र क्रमबद्धता" else "Filter & Sort Listings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = {
                        selectedCategory = "All"
                        selectedPriceFilter = PriceFilter.ALL
                        selectedSortOption = SortOption.FEATURED
                        showFilterSheet = false
                    }) {
                        Text(if (isNepali) "खाली गर्नुहोस्" else "Clear All")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sort By Section
                Text(
                    text = if (isNepali) "क्रमबद्ध गर्नुहोस् (Sort By)" else "Sort By",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SortOption.values().forEach { sort ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedSortOption = sort }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(
                                selected = selectedSortOption == sort,
                                onClick = { selectedSortOption = sort }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = if (isNepali) sort.labelNp else sort.labelEn, fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Price Filter Section
                Text(
                    text = if (isNepali) "मूल्य सीमा (Price Range)" else "Price Range",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PriceFilter.values()) { pf ->
                        FilterChip(
                            selected = selectedPriceFilter == pf,
                            onClick = { selectedPriceFilter = pf },
                            label = { Text(if (isNepali) pf.labelNp else pf.labelEn, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { showFilterSheet = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ConnectNpBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isNepali) "लागु गर्नुहोस्" else "Apply Filters", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Detail BottomSheet with Message Seller and Escrow buttons
    if (selectedItemForDetail != null) {
        val item = selectedItemForDetail!!
        val isMyItem = item.seller.id == currentUserId || item.seller.name == "ConnectNP Member"

        ModalBottomSheet(onDismissRequest = { selectedItemForDetail = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .navigationBarsPadding()
            ) {
                // Item Image
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    if (!item.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (item.imageResId != null) {
                        Image(
                            painter = painterResource(id = item.imageResId),
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.ShoppingBag, contentDescription = null, tint = ConnectNpBlue, modifier = Modifier.size(48.dp))
                        }
                    }

                    // Sold Overlay
                    if (item.isSold) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = NepalCrimson
                            ) {
                                Text(
                                    text = if (isNepali) "बिक्री भइसकेको (SOLD)" else "SOLD",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Price and Condition Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "रु ${item.priceNpr}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = ConnectNpBlue
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ConnectNpBlue.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = item.condition,
                                color = ConnectNpBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = item.category,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = item.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = NepalCrimson, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = item.location, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Seller Card Profile
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(ConnectNpBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.seller.name.take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.seller.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                if (item.seller.isVerified) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.Verified, contentDescription = "Verified", tint = ConnectNpBlue, modifier = Modifier.size(14.dp))
                                }
                            }
                            Text(
                                text = "🇳🇵 Verified ConnectNP Seller • Quick Response",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Description
                Text(
                    text = item.description,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons: Message Seller & Buy with Escrow
                if (isMyItem) {
                    // Own item notice
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ConnectNpBlue.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isNepali) "⭐ यो तपाईंको सूचीकरण हो" else "⭐ This is your listing",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = ConnectNpBlue
                            )
                            Button(
                                onClick = {
                                    selectedItemForDetail = null
                                    activeSection = MarketplaceSection.MY_LISTINGS
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ConnectNpBlue),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(if (isNepali) "व्यवस्थापन गर्नुहोस्" else "Manage in My Listings", fontSize = 11.sp)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // "Message Seller" Button (User Requirement #2)
                        Button(
                            onClick = {
                                onMessageSeller?.invoke(item)
                                selectedItemForDetail = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("message_seller_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ConnectNpBlue)
                        ) {
                            Icon(imageVector = Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isNepali) "बिक्रेतालाई सन्देश" else "Message Seller",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        // "Buy with Escrow" Button
                        Button(
                            onClick = {
                                onBuyEscrow(item)
                                selectedItemForDetail = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("buy_escrow_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NepalCrimson),
                            enabled = !item.isSold
                        ) {
                            Icon(imageVector = Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (item.isSold) (if (isNepali) "बिक्री भइसकेको" else "Sold Out") else (if (isNepali) "एस्क्रोबाट किन्नुहोस्" else "Buy Escrow"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Sell Listing Dialog
    if (showSellDialog) {
        SellListingDialog(
            isNepali = isNepali,
            onDismiss = { showSellDialog = false },
            onSubmit = { title, price, cat, loc, desc, uri ->
                onAddListing(title, price, cat, loc, desc, uri)
                showSellDialog = false
            }
        )
    }

    // Edit Price Dialog (Requirement #3 My Listings management)
    if (itemToEditPrice != null) {
        val item = itemToEditPrice!!
        var newPriceText by remember { mutableStateOf(item.priceNpr.toString()) }
        AlertDialog(
            onDismissRequest = { itemToEditPrice = null },
            title = { Text(if (isNepali) "मूल्य परिमार्जन गर्नुहोस्" else "Update Listing Price") },
            text = {
                Column {
                    Text(text = item.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newPriceText,
                        onValueChange = { newPriceText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Price in NPR (रु)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("edit_price_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = newPriceText.toLongOrNull()
                        if (parsed != null && parsed > 0) {
                            onUpdatePrice?.invoke(item.id, parsed)
                            itemToEditPrice = null
                        }
                    },
                    modifier = Modifier.testTag("save_price_button")
                ) {
                    Text(if (isNepali) "सुरक्षित गर्नुहोस्" else "Save Price")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToEditPrice = null }) {
                    Text(if (isNepali) "रद्द गर्नुहोस्" else "Cancel")
                }
            }
        )
    }

    // Confirm Delete Dialog (Requirement #3 My Listings management)
    if (itemToDelete != null) {
        val item = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(if (isNepali) "सामान हटाउन चाहनुहुन्छ?" else "Delete Listing?") },
            text = {
                Text(
                    if (isNepali) "के तपाईं पक्का '${item.title}' बजारबाट मेटाउन चाहनुहुन्छ?"
                    else "Are you sure you want to permanently delete '${item.title}' from CONNECTNP Marketplace?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteListing?.invoke(item.id)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NepalCrimson),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text(if (isNepali) "मेटाउनुहोस्" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(if (isNepali) "रद्द गर्नुहोस्" else "Cancel")
                }
            }
        )
    }
}

/**
 * Product Card for Explore Grid
 */
@Composable
fun ProductCard(
    item: MarketItem,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("market_card_${item.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(125.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            ) {
                if (!item.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (item.imageResId != null) {
                    Image(
                        painter = painterResource(id = item.imageResId),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.ShoppingBag, contentDescription = null, tint = ConnectNpBlue)
                    }
                }

                if (item.isSold) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = "SOLD",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else if (item.isFeatured) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = NepalCrimson,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = "Featured",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = "रु ${item.priceNpr}",
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = ConnectNpBlue
                )
                Text(
                    text = item.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = NepalCrimson,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = item.location,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * "My Listings" Management View (Requirement #3)
 */
@Composable
fun MyListingsView(
    myListings: List<MarketItem>,
    isNepali: Boolean,
    onAddNewListing: () -> Unit,
    onItemClick: (MarketItem) -> Unit,
    onEditPrice: (MarketItem) -> Unit,
    onToggleSold: (MarketItem) -> Unit,
    onDelete: (MarketItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("my_listings_section")
    ) {
        // Summary & Stats Bar
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isNepali) "मेरो सूचीकरण तथ्याङ्क" else "My Listings Summary",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${myListings.count { !it.isSold }} ${if (isNepali) "सक्रिय" else "Active"} • ${myListings.count { it.isSold }} ${if (isNepali) "बिक्री भइसकेको" else "Sold"}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onAddNewListing,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NepalCrimson),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isNepali) "+ नयाँ सामान" else "+ New Listing", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (myListings.isEmpty()) {
            // Empty State for My Listings
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(ConnectNpBlue.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            tint = ConnectNpBlue,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (isNepali) "तपाईंले कुनै सामान सूचीकृत गर्नुभएको छैन" else "No Listings Posted Yet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isNepali)
                            "नेपालभरिका लाखौं ग्राहकहरूलाई आफ्नो पुराना वा नयाँ सामान बेच्न तुरुन्त सुरु गर्नुहोस्।"
                        else
                            "Sell your electronics, handicrafts, vehicles or fashion directly to thousands of buyers across Nepal.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = onAddNewListing,
                        colors = ButtonDefaults.buttonColors(containerColor = NepalCrimson),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isNepali) "पहिलो सामान बेच्नुहोस्" else "Post Your First Item", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // List of User's Listings with management options
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(myListings, key = { it.id }) { item ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemClick(item) }
                            .testTag("my_listing_card_${item.id}")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Thumbnail
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                ) {
                                    if (!item.imageUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = item.imageUrl,
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else if (item.imageResId != null) {
                                        Image(
                                            painter = painterResource(id = item.imageResId),
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = ConnectNpBlue)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "रु ${item.priceNpr}",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
                                            color = ConnectNpBlue
                                        )
                                        // Status badge
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (item.isSold) Color.Gray.copy(alpha = 0.2f) else ConnectNpSuccess.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = if (item.isSold) (if (isNepali) "बिक्री भयो" else "SOLD") else (if (isNepali) "सक्रिय" else "ACTIVE"),
                                                color = if (item.isSold) Color.Gray else ConnectNpSuccess,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = item.title,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${item.category} • ${item.location}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(6.dp))

                            // Management Action Buttons: Edit Price, Mark Sold/Active, Delete
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Edit Price Button
                                OutlinedButton(
                                    onClick = { onEditPrice(item) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("edit_price_btn_${item.id}")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isNepali) "मूल्य फेर्नुहोस्" else "Edit Price", fontSize = 11.sp)
                                }

                                // Toggle Sold Status Button
                                OutlinedButton(
                                    onClick = { onToggleSold(item) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("toggle_sold_btn_${item.id}")
                                ) {
                                    Icon(
                                        imageVector = if (item.isSold) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (item.isSold) ConnectNpSuccess else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (item.isSold) (if (isNepali) "सक्रिय बनाउनुहोस्" else "Mark Active") else (if (isNepali) "बिक्री भयो भन्नुहोस्" else "Mark Sold"),
                                        fontSize = 11.sp
                                    )
                                }

                                // Delete Button
                                IconButton(
                                    onClick = { onDelete(item) },
                                    modifier = Modifier.size(32.dp).testTag("delete_item_btn_${item.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Listing",
                                        tint = NepalCrimson,
                                        modifier = Modifier.size(18.dp)
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

/**
 * Dialog to create a new Marketplace Listing
 */
@Composable
fun SellListingDialog(
    isNepali: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (title: String, price: Long, category: String, location: String, description: String, mediaUri: Uri?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Handicrafts") }
    var location by remember { mutableStateOf("Kathmandu, Nepal") }
    var description by remember { mutableStateOf("") }
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }

    val categories = listOf("Handicrafts", "Fashion", "Electronics", "Vehicles", "Property", "Home & Living")

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedMediaUri = uri
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isNepali) "सामान बिक्रीको सूची बनाउनुहोस्" else "Create Marketplace Listing",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Photo selector with preview
                if (selectedMediaUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(10.dp))
                    ) {
                        AsyncImage(
                            model = selectedMediaUri,
                            contentDescription = "Listing preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        IconButton(
                            onClick = { selectedMediaUri = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(28.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove photo", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = ConnectNpBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isNepali) "तस्बिर थप्नुहोस् (स्वचालित संकुचन)" else "Add Item Photo (Auto-Compressed)", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Item Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("listing_title_input")
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Price in NPR (रु)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("listing_price_input")
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Category selector
                Text("Category:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 11.sp) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location (e.g. Kathmandu, Pokhara)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val price = priceText.toLongOrNull() ?: 1000L
                        if (title.isNotBlank()) {
                            onSubmit(title, price, category, location, description, selectedMediaUri)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("publish_listing_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = NepalCrimson),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isNepali) "सूचीकृत गर्नुहोस्" else "Publish Listing", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
