package com.ritesh.cashiro.presentation.ui.features.categories

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.ritesh.cashiro.R
import com.ritesh.cashiro.presentation.ui.components.SearchBarBox

data class IconItem(
        val name: String,
        val category: String,
        val iconName: String,
        val resourceId: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconSelector(
    context: Context = LocalContext.current,
    selectedIconName: String?,
    onIconSelected: (String) -> Unit,
) {
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }

    val allIcons = remember { getAllIcons(context) }

    val filteredIcons =
            remember(searchQuery.text) {
                if (searchQuery.text.isEmpty()) {
                    allIcons
                } else {
                    allIcons.filter {
                        it.name.contains(searchQuery.text, ignoreCase = true) ||
                                it.category.contains(searchQuery.text, ignoreCase = true)
                    }
                }
            }

    val labels = listOf(stringResource(R.string.search_fruits), stringResource(R.string.search_shopping), stringResource(R.string.search_fitness), stringResource(R.string.search_sports))
    var currentLabelIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            currentLabelIndex = (currentLabelIndex + 1) % labels.size
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        SearchBarBox(
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            label = {
                AnimatedContent(
                    targetState = labels[currentLabelIndex],
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(400, delayMillis = 100)) +
                                slideInVertically(
                                    initialOffsetY = { it },
                                    animationSpec = tween(400, delayMillis = 100)
                                ))
                            .togetherWith(
                                fadeOut(animationSpec = tween(400)) +
                                        slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(400))
                            )
                    },
                    label = "SearchBarLabelAnimation"
                ) { labelText ->
                    Text(
                        text = labelText,
                        fontSize = 14.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.inverseSurface.copy(0.5f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(
                targetState = filteredIcons,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                            scaleIn(
                                    initialScale = 0.92f,
                                    animationSpec = tween(220, delayMillis = 90)
                            ) togetherWith fadeOut(animationSpec = tween(90))
                },
                label = "Filtered Icon animated"
        ) { iconsToDisplay ->
            IconFlowLayout(
                    icons = iconsToDisplay,
                    selectedIconName = selectedIconName,
                    onIconSelected = onIconSelected,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun IconFlowLayout(
    icons: List<IconItem>,
    selectedIconName: String?,
    onIconSelected: (String) -> Unit,
) {
    val themeColors = MaterialTheme.colorScheme
    val groupedIcons = icons.groupBy { it.category }

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    LazyColumn(
            modifier = Modifier.height(screenHeight),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        groupedIcons.forEach { (category, iconsInCategory) ->
            stickyHeader(key = "$category header") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    themeColors.surface,
                                    themeColors.surface.copy(alpha = 0.9f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                            text = category.uppercase(),
                            color = themeColors.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                    )
                }
            }

            item(key = category) {
                FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                ) {
                    iconsInCategory.forEach { icon ->
                        IconItemView(
                                icon = icon,
                                isSelected = icon.iconName == selectedIconName,
                                onClick = { onIconSelected(icon.iconName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IconItemView(
    icon: IconItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val themeColors = MaterialTheme.colorScheme
    val infiniteTransition = rememberInfiniteTransition(label = "Selected Glow animation")

    val animatedColor by
            infiniteTransition.animateColor(
                initialValue = themeColors.primary.copy(alpha = 0.5f),
                targetValue = themeColors.secondary.copy(alpha = 0.5f),
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(2000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                label = "Selected Glow animation"
            )

    Box(
        modifier = Modifier
            .then(
                if (isSelected) {
                    Modifier
                        .shadow(
                            8.dp,
                            RoundedCornerShape(16.dp),
                            spotColor = animatedColor
                        ).border(
                        2.dp,
                        animatedColor,
                        RoundedCornerShape(16.dp)
                    )
                } else {
                    Modifier
                }
            )
            .size(64.dp)
            .clip(RoundedCornerShape(16.dp))

            .clickable(onClick = onClick)
            .background(
                color = themeColors.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp)
            )

            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
                painter = painterResource(id = icon.resourceId),
                contentDescription = icon.name,
                modifier = Modifier.size(40.dp)
        )
    }
}

private fun getAllIcons(context: Context): List<IconItem> {
    val icons = mutableListOf<IconItem>()

    // Helper to add icons
    fun addIcon(name: String, category: String, resId: Int) {
        val iconName = try {
            context.resources.getResourceEntryName(resId)
        } catch (e: Exception) {
            "" // Fallback if resource name cannot be retrieved
        }
        icons.add(IconItem(name, category, iconName, resId))
    }

    // Animals
    addIcon("Bear", "Animals", R.drawable.type_animal_bear)
    addIcon("Bird", "Animals", R.drawable.type_animal_bird)
    addIcon("Cat", "Animals", R.drawable.type_animal_cat_face)
    addIcon("Dog", "Animals", R.drawable.type_animal_dog_face)
    addIcon("Fox", "Animals", R.drawable.type_animal_fox)
    addIcon("Lion", "Animals", R.drawable.type_animal_lion)
    addIcon("Panda", "Animals", R.drawable.type_animal_panda)
    addIcon("Tiger", "Animals", R.drawable.type_animal_tiger_face)
    addIcon("Chicken", "Animals", R.drawable.type_animal_chicken)
    addIcon("Cow", "Animals", R.drawable.type_animal_cow_face)
    addIcon("Dove", "Animals", R.drawable.type_animal_dove)
    addIcon("Frog", "Animals", R.drawable.type_animal_frog)
    addIcon("Goblin", "Animals", R.drawable.type_animal_goblin)
    addIcon("Hamster", "Animals", R.drawable.type_animal_hamster)
    addIcon("Hatching Chick", "Animals", R.drawable.type_animal_hatching_chick)
    addIcon("Koala", "Animals", R.drawable.type_animal_koala)
    addIcon("Lady Beetle", "Animals", R.drawable.type_animal_lady_beetle)
    addIcon("Monkey", "Animals", R.drawable.type_animal_monkey_face)
    addIcon("Mosquito", "Animals", R.drawable.type_animal_mosquito)
    addIcon("Mouse", "Animals", R.drawable.type_animal_mouse_face)
    addIcon("Octopus", "Animals", R.drawable.type_animal_octopus)
    addIcon("Orge", "Animals", R.drawable.type_animal_ogre)
    addIcon("Orangutan", "Animals", R.drawable.type_animal_orangutan)
    addIcon("Otter", "Animals", R.drawable.type_animal_otter)
    addIcon("Owl", "Animals", R.drawable.type_animal_owl)
    addIcon("Parrot", "Animals", R.drawable.type_animal_parrot)
    addIcon("Peacock", "Animals", R.drawable.type_animal_peacock)
    addIcon("Phoenix", "Animals", R.drawable.type_animal_phoenix_bird)
    addIcon("Pig", "Animals", R.drawable.type_animal_pig)
    addIcon("Pig Face", "Animals", R.drawable.type_animal_pig_face)
    addIcon("Polar Bear", "Animals", R.drawable.type_animal_polar_bear)
    addIcon("Rabbit", "Animals", R.drawable.type_animal_rabbit_face)
    addIcon("Raccoon", "Animals", R.drawable.type_animal_raccoon)
    addIcon("Whale", "Animals", R.drawable.type_animal_spouting_whale)
    addIcon("Squid", "Animals", R.drawable.type_animal_squid)
    addIcon("Wolf", "Animals", R.drawable.type_animal_wolf)

    // Beverages
    addIcon("Beer", "Beverages", R.drawable.type_beverages_beer)
    addIcon("Coffee", "Beverages", R.drawable.type_beverages_coffee)
    addIcon("Tea", "Beverages", R.drawable.type_beverages_tea)
    addIcon("Wine", "Beverages", R.drawable.type_beverages_wine_glass)
    addIcon("Cocktail", "Beverages", R.drawable.type_beverages_cocktail_glass)
    addIcon("Beverages", "Beverages", R.drawable.type_beverages_beverages)
    addIcon("Beverage Box", "Beverages", R.drawable.type_beverages_beverage_box)
    addIcon("Champaign", "Beverages", R.drawable.type_beverages_bottle_with_popping_cork)
    addIcon("Bubble Tea", "Beverages", R.drawable.type_beverages_bubble_tea)
    addIcon("Beer Mugs", "Beverages", R.drawable.type_beverages_clinking_beer_mugs)
    addIcon("Glasses", "Beverages", R.drawable.type_beverages_clinking_glasses)
    addIcon("Sake", "Beverages", R.drawable.type_beverages_sake)
    addIcon("Teapot", "Beverages", R.drawable.type_beverages_teapot)
    addIcon("Tropical Drink", "Beverages", R.drawable.type_beverages_tropical_drink)

    // Food
    addIcon("Hamburger", "Food", R.drawable.type_food_hamburger)
    addIcon("Pizza", "Food", R.drawable.type_food_pizza)
    addIcon("Taco", "Food", R.drawable.type_food_taco)
    addIcon("Ramen", "Food", R.drawable.type_food_ramen)
    addIcon("Sushi", "Food", R.drawable.type_food_sushi)
    addIcon("Bacon", "Food", R.drawable.type_food_bacon)
    addIcon("Bento", "Food", R.drawable.type_food_bento_box)
    addIcon("Burrito", "Food", R.drawable.type_food_burrito)
    addIcon("Canned Food", "Food", R.drawable.type_food_canned_food)
    addIcon("Cooked Rice", "Food", R.drawable.type_food_cooked_rice)
    addIcon("Cooking", "Food", R.drawable.type_food_cooking)
    addIcon("Curry Rice", "Food", R.drawable.type_food_curry_rice)
    addIcon("Dining", "Food", R.drawable.type_food_dining)
    addIcon("Fondue", "Food", R.drawable.type_food_fondue)
    addIcon("Fried Shrimp", "Food", R.drawable.type_food_fried_shrimp)
    addIcon("Green Salad", "Food", R.drawable.type_food_green_salad)
    addIcon("Hotdog", "Food", R.drawable.type_food_hot_dog)
    addIcon("Meat Bone", "Food", R.drawable.type_food_meat_on_bone)
    addIcon("Oyster", "Food", R.drawable.type_food_oyster)
    addIcon("Pot", "Food", R.drawable.type_food_pot_of_food)
    addIcon("Poultry leg", "Food", R.drawable.type_food_poultry_leg)
    addIcon("Rice Ball", "Food", R.drawable.type_food_rice_ball)
    addIcon("Sandwich", "Food", R.drawable.type_food_sandwich)
    addIcon("Pan Food", "Food", R.drawable.type_food_shallow_pan_of_food)
    addIcon("Saghetti", "Food", R.drawable.type_food_spaghetti)
    addIcon("Stuffed Flatbread", "Food", R.drawable.type_food_stuffed_flatbread)
    addIcon("Takeout", "Food", R.drawable.type_food_takeout)

    // Fruits
    addIcon("Apple", "Fruits", R.drawable.type_fruit_red_apple)
    addIcon("Banana", "Fruits", R.drawable.type_fruit_banana)
    addIcon("Strawberry", "Fruits", R.drawable.type_fruit_strawberry)
    addIcon("Grapes", "Fruits", R.drawable.type_fruit_grapes)
    addIcon("Mango", "Fruits", R.drawable.type_fruit_mango)
    addIcon("Avocado", "Fruits", R.drawable.type_fruit_avocado)
    addIcon("Tomato", "Fruits", R.drawable.type_fruit_tomato)
    addIcon("Pineapple", "Fruits", R.drawable.type_fruit_pineapple)
    addIcon("Banana", "Fruits", R.drawable.type_fruit_banana)
    addIcon("Blueberries", "Fruits", R.drawable.type_fruit_blueberries)
    addIcon("Cherries", "Fruits", R.drawable.type_fruit_cherries)
    addIcon("Chestnut", "Fruits", R.drawable.type_fruit_chestnut)
    addIcon("Coconut", "Fruits", R.drawable.type_fruit_coconut)
    addIcon("Corn", "Fruits", R.drawable.type_fruit_ear_of_corn)
    addIcon("Green Apple", "Fruits", R.drawable.type_fruit_green_apple)
    addIcon("Kiwi", "Fruits", R.drawable.type_fruit_kiwi_fruit)
    addIcon("Lemon", "Fruits", R.drawable.type_fruit_lemon)
    addIcon("Lime", "Fruits", R.drawable.type_fruit_lime)
    addIcon("Melon", "Fruits", R.drawable.type_fruit_melon)
    addIcon("Olive", "Fruits", R.drawable.type_fruit_olive)
    addIcon("Peach", "Fruits", R.drawable.type_fruit_peach)
    addIcon("Pear", "Fruits", R.drawable.type_fruit_pear)
    addIcon("Tangerine", "Fruits", R.drawable.type_fruit_tangerine)

    // Groceries
    addIcon("Steak", "Groceries", R.drawable.type_groceries_cut_of_meat)
    addIcon("Egg", "Groceries", R.drawable.type_groceries_egg)
    addIcon("Fish", "Groceries", R.drawable.type_groceries_fish)
    addIcon("Bread", "Groceries", R.drawable.type_groceries_bread)
    addIcon("Baby Milk", "Groceries", R.drawable.type_groceries_baby_bottle)
    addIcon("Milk", "Groceries", R.drawable.type_groceries_glass_of_milk)
    addIcon("Baguette Bread", "Groceries", R.drawable.type_groceries_baguette_bread)
    addIcon("Basket", "Groceries", R.drawable.type_groceries_basket)
    addIcon("Butter", "Groceries", R.drawable.type_groceries_butter)
    addIcon("Basket", "Groceries", R.drawable.type_groceries_cheese_wedge)
    addIcon("Flatbread", "Groceries", R.drawable.type_groceries_flatbread)
    addIcon("Soap", "Groceries", R.drawable.type_groceries_soap)
    addIcon("Candle", "Groceries", R.drawable.type_groceries_candle)
    addIcon("Lotion Bottle", "Groceries", R.drawable.type_groceries_lotion_bottle)
    addIcon("Sponge", "Groceries", R.drawable.type_groceries_sponge)
    addIcon("Spoon", "Groceries", R.drawable.type_groceries_spoon)
    addIcon("Toothbrush", "Groceries", R.drawable.type_groceries_toothbrush)

    // Shopping
    addIcon("Cart", "Shopping", R.drawable.type_shopping_shopping_cart)
    addIcon("Bag", "Shopping", R.drawable.type_shopping_shopping_bags)
    addIcon("Dress", "Shopping", R.drawable.type_shopping_dress)
    addIcon("Shirt", "Shopping", R.drawable.type_shopping_t_shirt)
    addIcon("Jeans", "Shopping", R.drawable.type_shopping_jeans)
    addIcon("Purse", "Shopping", R.drawable.type_shopping_purse)
    addIcon("Backpacks", "Shopping", R.drawable.type_shopping_backpack)
    addIcon("Ballet Shoes", "Shopping", R.drawable.type_shopping_ballet_shoes)
    addIcon("Bikini", "Shopping", R.drawable.type_shopping_bikini)
    addIcon("Briefcase", "Shopping", R.drawable.type_shopping_briefcase)
    addIcon("Cap", "Shopping", R.drawable.type_shopping_billed_cap)
    addIcon("Clutch Bag", "Shopping", R.drawable.type_shopping_clutch_bag)
    addIcon("Coat", "Shopping", R.drawable.type_shopping_coat)
    addIcon("Gem Stone", "Shopping", R.drawable.type_shopping_gem_stone)
    addIcon("Glasses", "Shopping", R.drawable.type_shopping_glasses)
    addIcon("Gloves", "Shopping", R.drawable.type_shopping_gloves)
    addIcon("Goggles", "Shopping", R.drawable.type_shopping_goggles)
    addIcon("Handbag", "Shopping", R.drawable.type_shopping_handbag)
    addIcon("High Heeled Shoes", "Shopping", R.drawable.type_shopping_high_heeled_shoe)
    addIcon("Hiking Boot", "Shopping", R.drawable.type_shopping_hiking_boot)
    addIcon("Kimono", "Shopping", R.drawable.type_shopping_kimono)
    addIcon("Lab Coat", "Shopping", R.drawable.type_shopping_lab_coat)
    addIcon("Lipstick", "Shopping", R.drawable.type_shopping_lipstick)
    addIcon("Shoes", "Shopping", R.drawable.type_shopping_mans_shoe)
    addIcon("Martial Arts", "Shopping", R.drawable.type_shopping_martial_arts_uniform)
    addIcon("Nail Polish", "Shopping", R.drawable.type_shopping_nail_polish)
    addIcon("Necktie", "Shopping", R.drawable.type_shopping_necktie)
    addIcon("Swimsuit", "Shopping", R.drawable.type_shopping_one_piece_swimsuit)
    addIcon("Sari", "Shopping", R.drawable.type_shopping_sari)
    addIcon("Scarf", "Shopping", R.drawable.type_shopping_scarf)
    addIcon("Shorts", "Shopping", R.drawable.type_shopping_shorts)
    addIcon("Socks", "Shopping", R.drawable.type_shopping_socks)
    addIcon("Sunglasses", "Shopping", R.drawable.type_shopping_sunglasses)
    addIcon("Top Hat", "Shopping", R.drawable.type_shopping_top_hat)

    // Finance
    addIcon("Bank", "Finance", R.drawable.type_finance_bank)
    addIcon("Wallet", "Finance", R.drawable.type_shopping_purse)
    addIcon("Bill", "Finance", R.drawable.type_finance_dollar_banknote)
    addIcon("Chart", "Finance", R.drawable.type_finance_bar_chart)
    addIcon("Chart Decreasing", "Finance", R.drawable.type_finance_chart_decreasing)
    addIcon("Chart Increasing", "Finance", R.drawable.type_finance_chart_increasing)
    addIcon("Coin", "Finance", R.drawable.type_finance_coin)
    addIcon("Yen Chart", "Finance", R.drawable.type_finance_chart_increasing_with_yen)
    addIcon("Building", "Finance", R.drawable.type_finance_classical_building)
    addIcon("Currency Exchange", "Finance", R.drawable.type_finance_currency_exchange)
    addIcon("Euro", "Finance", R.drawable.type_finance_euro_banknote)
    addIcon("Dollar Sign", "Finance", R.drawable.type_finance_heavy_dollar_sign)
    addIcon("Money Bag", "Finance", R.drawable.type_finance_money_bag)
    addIcon("Money Bag Wings", "Finance", R.drawable.type_finance_money_with_wings)
    addIcon("Pound", "Finance", R.drawable.type_finance_pound_banknote)
    addIcon("Yen", "Finance", R.drawable.type_finance_yen_banknote)
    addIcon("Crypto", "Finance", R.drawable.type_finance_crypto)
    addIcon("Credit Card", "Finance", R.drawable.type_finance_credit_card)
    addIcon("Deposit", "Finance", R.drawable.type_finance_deposit)
    addIcon("Insurance", "Finance", R.drawable.type_finance_insurance)
    addIcon("Tip", "Finance", R.drawable.type_finance_tip)
    addIcon("Tax", "Finance", R.drawable.type_finance_tax_due)

    // Travel
    addIcon("Airplane", "Travel", R.drawable.type_travel_transport_airplane)
    addIcon("Bus", "Travel", R.drawable.type_travel_transport_bus)
    addIcon("Car", "Travel", R.drawable.type_travel_transport_automobile)
    addIcon("Train", "Travel", R.drawable.type_travel_transport_bullet_train)
    addIcon("Taxi", "Travel", R.drawable.type_travel_transport_taxi)
    addIcon("Admission Tickets", "Travel", R.drawable.type_travel_transport_admission_tickets)
    addIcon("Aerial Tramway", "Travel", R.drawable.type_travel_transport_aerial_tramway)
    addIcon("Airplane Arrival", "Travel", R.drawable.type_travel_transport_airplane_arrival)
    addIcon("Airplane Departure", "Travel", R.drawable.type_travel_transport_airplane_departure)
    addIcon("Auto Rickshaw", "Travel", R.drawable.type_travel_transport_auto_rickshaw)
    addIcon("Canoe", "Travel", R.drawable.type_travel_transport_canoe)
    addIcon("Delivery Truck", "Travel", R.drawable.type_travel_transport_delivery_truck)
    addIcon("Fuel Pump", "Travel", R.drawable.type_travel_transport_fuel_pump)
    addIcon("Globe", "Travel", R.drawable.type_travel_transport_globe_showing_asia_australia)
    addIcon("Helicopter", "Travel", R.drawable.type_travel_transport_helicopter)
    addIcon("High Speed Train", "Travel", R.drawable.type_travel_transport_high_speed_train)
    addIcon("Inbox Tray", "Travel", R.drawable.type_travel_transport_inbox_tray)
    addIcon("Light Rail", "Travel", R.drawable.type_travel_transport_light_rail)
    addIcon("Luggage", "Travel", R.drawable.type_travel_transport_luggage)
    addIcon("Metro", "Travel", R.drawable.type_travel_transport_metro)
    addIcon("Minibus", "Travel", R.drawable.type_travel_transport_minibus)
    addIcon("Monorail", "Travel", R.drawable.type_travel_transport_monorail)
    addIcon("Motor Boat", "Travel", R.drawable.type_travel_transport_motor_boat)
    addIcon("Motor Scooter", "Travel", R.drawable.type_travel_transport_motor_scooter)
    addIcon("Motorcycle", "Travel", R.drawable.type_travel_transport_motorcycle)
    addIcon("Mountain Cableway", "Travel", R.drawable.type_travel_transport_mountain_cableway)
    addIcon("Mountain Railway", "Travel", R.drawable.type_travel_transport_mountain_railway)
    addIcon("Oncoming Automobile", "Travel", R.drawable.type_travel_transport_oncoming_automobile)
    addIcon("Oncoming Bus", "Travel", R.drawable.type_travel_transport_oncoming_bus)
    addIcon("Oncoming taxi", "Travel", R.drawable.type_travel_transport_oncoming_taxi)
    addIcon("Oncoming Police Car", "Travel", R.drawable.type_travel_transport_oncoming_police_car)
    addIcon("Mailbox", "Travel", R.drawable.type_travel_transport_open_mailbox_with_lowered_flag)
    addIcon("Mailbox Flag", "Travel", R.drawable.type_travel_transport_open_mailbox_with_raised_flag)
    addIcon("Outbox Tray", "Travel", R.drawable.type_travel_transport_outbox_tray)
    addIcon("Package", "Travel", R.drawable.type_travel_transport_package)
    addIcon("Ship", "Travel", R.drawable.type_travel_transport_passenger_ship)
    addIcon("Pickup Truck", "Travel", R.drawable.type_travel_transport_pickup_truck)
    addIcon("Small Airplane", "Travel", R.drawable.type_travel_transport_small_airplane)
    addIcon("Speedboat", "Travel", R.drawable.type_travel_transport_speedboat)
    addIcon("Tickets", "Travel", R.drawable.type_travel_transport_ticket)
    addIcon("Inventory", "Travel", R.drawable.type_travel_transport_inventory)


    // Health
    addIcon("Hospital", "Health", R.drawable.type_health_hospital)
    addIcon("Pill", "Health", R.drawable.type_health_pill)
    addIcon("Stethoscope", "Health", R.drawable.type_health_stethoscope)
    addIcon("DNA", "Health", R.drawable.type_health_dna)
    addIcon("Tooth", "Health", R.drawable.type_health_tooth)
    addIcon("Bandage", "Health", R.drawable.type_health_adhesive_bandage)
    addIcon("Ambulance", "Health", R.drawable.type_health_ambulance)
    addIcon("Cigarette", "Health", R.drawable.type_health_cigarette)
    addIcon("Blood", "Health", R.drawable.type_health_drop_of_blood)
    addIcon("Health Worker", "Health", R.drawable.type_health_health_worker_light)
    addIcon("Mending Heart", "Health", R.drawable.type_health_mending_heart)
    addIcon("Syringe", "Health", R.drawable.type_health_syringe)
    addIcon("Thermometer", "Health", R.drawable.type_health_thermometer)
    addIcon("X-ray", "Health", R.drawable.type_health_x_ray)
    addIcon("Vet", "Health", R.drawable.type_health_vet)

    // Tools
    addIcon("Game", "Tools", R.drawable.type_tool_electronic_video_game)
    addIcon("Movie", "Tools", R.drawable.type_tool_electronic_movie_camera)
    addIcon("Music", "Tools", R.drawable.type_tool_electronic_headphone)
    addIcon("Movie Clapper", "Tools", R.drawable.type_tool_electronic_clapper_board)
    addIcon("Axe", "Tools", R.drawable.type_tool_electronic_axe)
    addIcon("Alarm Clock", "Tools", R.drawable.type_tool_electronic_alarm_clock)
    addIcon("Ballot Box", "Tools", R.drawable.type_tool_electronic_ballot_box_with_ballot)
    addIcon("Camera", "Tools", R.drawable.type_tool_electronic_camera)
    addIcon("Camera Flash", "Tools", R.drawable.type_tool_electronic_camera_with_flash)
    addIcon("Carpentry Saw", "Tools", R.drawable.type_tool_electronic_carpentry_saw)
    addIcon("Chopsticks", "Tools", R.drawable.type_tool_electronic_chopsticks)
    addIcon("Clock", "Tools", R.drawable.type_tool_electronic_clock)
    addIcon("Compass", "Tools", R.drawable.type_tool_electronic_compass)
    addIcon("Computer Disk", "Tools", R.drawable.type_tool_electronic_computer_disk)
    addIcon("Computer Mouse", "Tools", R.drawable.type_tool_electronic_computer_mouse)
    addIcon("Dagger", "Tools", R.drawable.type_tool_electronic_dagger)
    addIcon("Desktop", "Tools", R.drawable.type_tool_electronic_desktop_computer)
    addIcon("DVD", "Tools", R.drawable.type_tool_electronic_dvd)
    addIcon("Fax Machine", "Tools", R.drawable.type_tool_electronic_fax_machine)
    addIcon("Film Frames", "Tools", R.drawable.type_tool_electronic_film_frames)
    addIcon("Film Projectors", "Tools", R.drawable.type_tool_electronic_film_projector)
    addIcon("Flashlight", "Tools", R.drawable.type_tool_electronic_flashlight)
    addIcon("Floppy Disk", "Tools", R.drawable.type_tool_electronic_floppy_disk)
    addIcon("Flower Card", "Tools", R.drawable.type_tool_electronic_flower_playing_cards)
    addIcon("Gear", "Tools", R.drawable.type_tool_electronic_gear)
    addIcon("Hammer", "Tools", R.drawable.type_tool_electronic_hammer)
    addIcon("Hammer Pick", "Tools", R.drawable.type_tool_electronic_hammer_and_pick)
    addIcon("Hammer Wrench", "Tools", R.drawable.type_tool_electronic_hammer_and_wrench)
    addIcon("Headphone", "Tools", R.drawable.type_tool_electronic_headphone)
    addIcon("High Voltage", "Tools", R.drawable.type_tool_electronic_high_voltage)
    addIcon("Hour Glass", "Tools", R.drawable.type_tool_electronic_hourglass_not_done)
    addIcon("Joystick", "Tools", R.drawable.type_tool_electronic_joystick)
    addIcon("Key", "Tools", R.drawable.type_tool_electronic_key)
    addIcon("Knife", "Tools", R.drawable.type_tool_electronic_kitchen_knife)
    addIcon("Ladder", "Tools", R.drawable.type_tool_electronic_ladder)
    addIcon("Laptop", "Tools", R.drawable.type_tool_electronic_laptop)
    addIcon("Level Slider", "Tools", R.drawable.type_tool_electronic_level_slider)
    addIcon("Bulb", "Tools", R.drawable.type_tool_electronic_light_bulb)
    addIcon("Lock Key", "Tools", R.drawable.type_tool_electronic_locked_with_key)
    addIcon("Loudspeaker", "Tools", R.drawable.type_tool_electronic_loudspeaker)
    addIcon("Magnifying Glass left", "Tools", R.drawable.type_tool_electronic_magnifying_glass_tilted_left)
    addIcon("Magnifying Glass right", "Tools", R.drawable.type_tool_electronic_magnifying_glass_tilted_right)
    addIcon("Megaphone", "Tools", R.drawable.type_tool_electronic_megaphone)
    addIcon("Microphone", "Tools", R.drawable.type_tool_electronic_microphone)
    addIcon("Mobile", "Tools", R.drawable.type_tool_electronic_mobile_phone)
    addIcon("Nut Bolt", "Tools", R.drawable.type_tool_electronic_nut_and_bolt)
    addIcon("Old Key", "Tools", R.drawable.type_tool_electronic_old_key)
    addIcon("One OClock", "Tools", R.drawable.type_tool_electronic_one_oclock)
    addIcon("Optical Disk", "Tools", R.drawable.type_tool_electronic_optical_disk)
    addIcon("Pick", "Tools", R.drawable.type_tool_electronic_pick)
    addIcon("Printer", "Tools", R.drawable.type_tool_electronic_printer)
    addIcon("Scissors", "Tools", R.drawable.type_tool_electronic_scissors)
    addIcon("Screwdriver", "Tools", R.drawable.type_tool_electronic_screwdriver)
    addIcon("Stopwatch", "Tools", R.drawable.type_tool_electronic_stopwatch)
    addIcon("Studio Microphone", "Tools", R.drawable.type_tool_electronic_studio_microphone)
    addIcon("Telephone", "Tools", R.drawable.type_tool_electronic_telephone)
    addIcon("Telescope", "Tools", R.drawable.type_tool_electronic_telescope)
    addIcon("Toolbox", "Tools", R.drawable.type_tool_electronic_toolbox)
    addIcon("Cassette", "Tools", R.drawable.type_tool_electronic_videocassette)
    addIcon("Watch", "Tools", R.drawable.type_tool_electronic_watch)
    addIcon("Wrench", "Tools", R.drawable.type_tool_electronic_wrench)
    addIcon("Software", "Tools", R.drawable.type_tool_electronic_software)


    // Vegetables
    addIcon("Beans", "Vegetables", R.drawable.type_vegetable_beans)
    addIcon("Bell Pepper", "Vegetables", R.drawable.type_vegetable_bell_pepper)
    addIcon("Broccoli", "Vegetables", R.drawable.type_vegetable_broccoli)
    addIcon("Carrot", "Vegetables", R.drawable.type_vegetable_carrot)
    addIcon("Cucumber", "Vegetables", R.drawable.type_vegetable_cucumber)
    addIcon("Eggplant", "Vegetables", R.drawable.type_vegetable_eggplant)
    addIcon("Garlic", "Vegetables", R.drawable.type_vegetable_garlic)
    addIcon("Ginger", "Vegetables", R.drawable.type_vegetable_ginger_root)
    addIcon("Hot Pepper", "Vegetables", R.drawable.type_vegetable_hot_pepper)
    addIcon("Brown Mushroom", "Vegetables", R.drawable.type_vegetable_brown_mushroom)
    addIcon("Mushroom", "Vegetables", R.drawable.type_vegetable_mushroom)
    addIcon("Onion", "Vegetables", R.drawable.type_vegetable_onion)
    addIcon("Pea Pod", "Vegetables", R.drawable.type_vegetable_pea_pod)
    addIcon("Potato", "Vegetables", R.drawable.type_vegetable_potato)

    // Snacks
    addIcon("Cookie", "Snacks", R.drawable.type_snack_cookie)
    addIcon("Dumpling", "Snacks", R.drawable.type_snack_dumpling)
    addIcon("Fortune Cookie", "Snacks", R.drawable.type_snack_fortune_cookie)
    addIcon("French Fries", "Snacks", R.drawable.type_snack_french_fries)
    addIcon("Peanuts", "Snacks", R.drawable.type_snack_peanuts)
    addIcon("Popcorn", "Snacks", R.drawable.type_snack_popcorn)
    addIcon("Rice Cracker", "Snacks", R.drawable.type_snack_rice_cracker)
    addIcon("Fish Cake", "Snacks", R.drawable.type_snack_fish_cake_with_swirl)

    // Sweets
    addIcon("Candy", "Sweets", R.drawable.type_sweet_candy)
    addIcon("Chocolate Bar", "Sweets", R.drawable.type_sweet_chocolate_bar)
    addIcon("Cupcake", "Sweets", R.drawable.type_sweet_cupcake)
    addIcon("Doughnut", "Sweets", R.drawable.type_sweet_doughnut)
    addIcon("Ice Cream", "Sweets", R.drawable.type_sweet_ice_cream)
    addIcon("Shaved Ice", "Sweets", R.drawable.type_sweet_shaved_ice)
    addIcon("Soft Ice Cream", "Sweets", R.drawable.type_sweet_soft_ice_cream)
    addIcon("Lollipop", "Sweets", R.drawable.type_sweet_lollipop)
    addIcon("Pancakes", "Sweets", R.drawable.type_sweet_pancakes)
    addIcon("Pie", "Sweets", R.drawable.type_sweet_pie)
    addIcon("Shortcake", "Sweets", R.drawable.type_sweet_shortcake)
    addIcon("Waffle", "Sweets", R.drawable.type_sweet_waffle)
    addIcon("Bagel", "Sweets", R.drawable.type_sweet_bagel)
    addIcon("Birthday Cake", "Sweets", R.drawable.type_sweet_birthday_cake)
    addIcon("Croissant", "Sweets", R.drawable.type_sweet_croissant)
    addIcon("Custard", "Sweets", R.drawable.type_sweet_custard)
    addIcon("Dango", "Sweets", R.drawable.type_sweet_dango)
    addIcon("Falafel", "Sweets", R.drawable.type_sweet_falafel)
    addIcon("Honey Pot", "Sweets", R.drawable.type_sweet_honey_pot)
    addIcon("Oden", "Sweets", R.drawable.type_sweet_oden)
    addIcon("Roasted Sweet Potato", "Sweets", R.drawable.type_sweet_roasted_sweet_potato)

    // Sports
    addIcon("Badminton", "Sports", R.drawable.type_sports_badminton)
    addIcon("Baseball", "Sports", R.drawable.type_sports_baseball)
    addIcon("Basketball", "Sports", R.drawable.type_sports_basketball)
    addIcon("Cricket", "Sports", R.drawable.type_sports_cricket_game)
    addIcon("Field Hockey", "Sports", R.drawable.type_sports_field_hockey)
    addIcon("Football", "Sports", R.drawable.type_sports_american_football)
    addIcon("Golf", "Sports", R.drawable.type_sports_flag_in_hole)
    addIcon("Ping Pong", "Sports", R.drawable.type_sports_ping_pong)
    addIcon("Soccer Ball", "Sports", R.drawable.type_sports_soccer_ball)
    addIcon("SoftBall", "Sports", R.drawable.type_sports_softball)
    addIcon("Tennis", "Sports", R.drawable.type_sports_tennis)
    addIcon("Trophy", "Sports", R.drawable.type_sports_trophy)
    addIcon("Sports Medal", "Sports", R.drawable.type_sports_sports_medal)
    addIcon("Military Medal", "Sports", R.drawable.type_sports_military_medal)
    addIcon("1st place medal", "Sports", R.drawable.type_sports_1st_place_medal)
    addIcon("2nd place medal", "Sports", R.drawable.type_sports_2nd_place_medal)
    addIcon("3rd place medal", "Sports", R.drawable.type_sports_3rd_place_medal)
    addIcon("Boomerang", "Sports", R.drawable.type_sports_boomerang)
    addIcon("Bow and Arrow", "Sports", R.drawable.type_sports_bow_and_arrow)
    addIcon("Bowling", "Sports", R.drawable.type_sports_bowling)
    addIcon("Boxing", "Sports", R.drawable.type_sports_boxing_glove)
    addIcon("Bullseye", "Sports", R.drawable.type_sports_bullseye)
    addIcon("Chequered Flag", "Sports", R.drawable.type_sports_chequered_flag)
    addIcon("Chess", "Sports", R.drawable.type_sports_chess_pawn)
    addIcon("Cricket", "Sports", R.drawable.type_sports_cricket_game)
    addIcon("Diving", "Sports", R.drawable.type_sports_diving_mask)
    addIcon("Hockey", "Sports", R.drawable.type_sports_field_hockey)
    addIcon("Fishing", "Sports", R.drawable.type_sports_fishing_pole)
    addIcon("Golf", "Sports", R.drawable.type_sports_flag_in_hole)
    addIcon("Biceps Arms", "Sports", R.drawable.type_sports_flexed_biceps_light)
    addIcon("Die", "Sports", R.drawable.type_sports_game_die)
    addIcon("Ice Hockey", "Sports", R.drawable.type_sports_ice_hockey)
    addIcon("Ice Skate", "Sports", R.drawable.type_sports_ice_skate)
    addIcon("Lacrosse", "Sports", R.drawable.type_sports_lacrosse)
    addIcon("Gym", "Sports", R.drawable.type_sports_man_lifting_weights)
    addIcon("Gym Women", "Sports", R.drawable.type_sports_woman_lifting_weights)
    addIcon("Mechanical Arm", "Sports", R.drawable.type_sports_mechanical_arm)
    addIcon("PingPong", "Sports", R.drawable.type_sports_ping_pong)
    addIcon("Pool Ball", "Sports", R.drawable.type_sports_pool_8_ball)
    addIcon("Rugby", "Sports", R.drawable.type_sports_rugby_football)
    addIcon("Skateboard", "Sports", R.drawable.type_sports_skateboard)
    addIcon("Snowboard", "Sports", R.drawable.type_sports_snowboarder_light)
    addIcon("Volleyball", "Sports", R.drawable.type_sports_volleyball)

    // Stationery
    addIcon("Pencil", "Stationery", R.drawable.type_stationary_pencil)
    addIcon("Pen", "Stationery", R.drawable.type_stationary_pen)
    addIcon("Fountain Pen", "Stationery", R.drawable.type_stationary_fountain_pen)
    addIcon("Paintbrush", "Stationery", R.drawable.type_stationary_paintbrush)
    addIcon("Notebook", "Stationery", R.drawable.type_stationary_notebook)
    addIcon("Notebook Decorative", "Stationery", R.drawable.type_stationary_notebook_with_decorative_cover)
    addIcon("Books", "Stationery", R.drawable.type_stationary_books)
    addIcon("Closed Book", "Stationery", R.drawable.type_stationary_closed_book)
    addIcon("Green Book", "Stationery", R.drawable.type_stationary_green_book)
    addIcon("Blue Book", "Stationery", R.drawable.type_stationary_blue_book)
    addIcon("Orange Book", "Stationery", R.drawable.type_stationary_orange_book)
    addIcon("Open Book", "Stationery", R.drawable.type_stationary_open_book)
    addIcon("Ledger", "Stationery", R.drawable.type_stationary_ledger)
    addIcon("Paperclip", "Stationery", R.drawable.type_stationary_paperclip)
    addIcon("Ruler", "Stationery", R.drawable.type_stationary_straight_ruler)
    addIcon("Artist Palette", "Stationery", R.drawable.type_stationary_artist_palette)
    addIcon("Bookmark", "Stationery", R.drawable.type_stationary_bookmark)
    addIcon("Label", "Stationery", R.drawable.type_stationary_label)
    addIcon("Bookmark Tabs", "Stationery", R.drawable.type_stationary_bookmark_tabs)
    addIcon("Files Box", "Stationery", R.drawable.type_stationary_card_file_box)
    addIcon("Card Index", "Stationery", R.drawable.type_stationary_card_index)
    addIcon("Clipboard", "Stationery", R.drawable.type_stationary_clipboard)
    addIcon("Link", "Stationery", R.drawable.type_stationary_link)
    addIcon("Link Paperclips", "Stationery", R.drawable.type_stationary_linked_paperclips)
    addIcon("Paperclip", "Stationery", R.drawable.type_stationary_paperclip)
    addIcon("Memo", "Stationery", R.drawable.type_stationary_memo)
    addIcon("Musical score", "Stationery", R.drawable.type_stationary_musical_score)
    addIcon("Newspaper", "Stationery", R.drawable.type_stationary_newspaper)
    addIcon("Page", "Stationery", R.drawable.type_stationary_page_facing_up)
    addIcon("page Curl", "Stationery", R.drawable.type_stationary_page_with_curl)
    addIcon("Scroll", "Stationery", R.drawable.type_stationary_scroll)
    addIcon("Spiral notepad", "Stationery", R.drawable.type_stationary_spiral_notepad)
    addIcon("Pushpin", "Stationery", R.drawable.type_stationary_pushpin)
    addIcon("Reminder Ribbon", "Stationery", R.drawable.type_stationary_reminder_ribbon)
    addIcon("Ribbon", "Stationery", R.drawable.type_stationary_ribbon)
    addIcon("Wrapped Gift", "Stationery", R.drawable.type_stationary_wrapped_gift)
    addIcon("Writing hand", "Stationery", R.drawable.type_stationary_writing_hand_light)
    addIcon("Toys", "Stationery", R.drawable.type_stationary_toys)
    addIcon("Gift Card", "Stationery", R.drawable.type_stationary_gift_card)

    // Musical Instruments
    addIcon("Guitar", "Musical Instruments", R.drawable.type_musical_instrument_guitar)
    addIcon("Keyboard", "Musical Instruments", R.drawable.type_musical_instrument_musical_keyboard)
    addIcon("Saxophone", "Musical Instruments", R.drawable.type_musical_instrument_saxophone)
    addIcon("Trumpet", "Musical Instruments", R.drawable.type_musical_instrument_trumpet)
    addIcon("Violin", "Musical Instruments", R.drawable.type_musical_instrument_violin)
    addIcon("Banjo", "Musical Instruments", R.drawable.type_musical_instrument_banjo)
    addIcon("Flute", "Musical Instruments", R.drawable.type_musical_instrument_flute)
    addIcon("Accordion", "Musical Instruments", R.drawable.type_musical_instrument_accordion)
    addIcon("Drum", "Musical Instruments", R.drawable.type_event_and_place_drum)

    // Flowers and Nature
    addIcon("Blossom", "Flowers & Nature", R.drawable.type_flower_and_tree_blossom)
    addIcon("Cactus", "Flowers & Nature", R.drawable.type_flower_and_tree_cactus)
    addIcon("Cherry Blossom", "Flowers & Nature", R.drawable.type_flower_and_tree_cherry_blossom)
    addIcon("Hibiscus", "Flowers & Nature", R.drawable.type_flower_and_tree_hibiscus)
    addIcon("Sunflower", "Flowers & Nature", R.drawable.type_flower_and_tree_sunflower)
    addIcon("Tulip", "Flowers & Nature", R.drawable.type_flower_and_tree_tulip)
    addIcon("Palm Tree", "Flowers & Nature", R.drawable.type_flower_and_tree_palm_tree)
    addIcon("Evergreen Tree", "Flowers & Nature", R.drawable.type_flower_and_tree_evergreen_tree)
    addIcon("Deciduous Tree", "Flowers & Nature", R.drawable.type_flower_and_tree_deciduous_tree)
    addIcon("Bouquet", "Flowers & Nature", R.drawable.type_flower_and_tree_bouquet)
    addIcon("Tree", "Flowers & Nature", R.drawable.type_flower_and_tree_deciduous_tree)
    addIcon("Evergreen tree", "Flowers & Nature", R.drawable.type_flower_and_tree_evergreen_tree)
    addIcon("Four Clover", "Flowers & Nature", R.drawable.type_flower_and_tree_four_leaf_clover)
    addIcon("Herb", "Flowers & Nature", R.drawable.type_flower_and_tree_herb)
    addIcon("Hyacinth", "Flowers & Nature", R.drawable.type_flower_and_tree_hyacinth)
    addIcon("Leafs", "Flowers & Nature", R.drawable.type_flower_and_tree_leaf_fluttering_in_wind)
    addIcon("Leafy Green", "Flowers & Nature", R.drawable.type_flower_and_tree_leafy_green)
    addIcon("Lotus", "Flowers & Nature", R.drawable.type_flower_and_tree_lotus)
    addIcon("Maple Leaf", "Flowers & Nature", R.drawable.type_flower_and_tree_maple_leaf)
    addIcon("Palm Tree", "Flowers & Nature", R.drawable.type_flower_and_tree_palm_tree)
    addIcon("Pine", "Flowers & Nature", R.drawable.type_flower_and_tree_pine_decoration)
    addIcon("Potted Plant", "Flowers & Nature", R.drawable.type_flower_and_tree_potted_plant)
    addIcon("Tanabata Tree", "Flowers & Nature", R.drawable.type_flower_and_tree_tanabata_tree)
    addIcon("White Flower", "Flowers & Nature", R.drawable.type_flower_and_tree_white_flower)

    // Events and Places
    addIcon("House", "Events & Places", R.drawable.type_event_and_place_house)
    addIcon("Houses", "Events & Places", R.drawable.type_event_and_place_houses)
    addIcon("Hotel", "Events & Places", R.drawable.type_event_and_place_hotel)
    addIcon("House Garden", "Events & Places", R.drawable.type_event_and_place_house_with_garden)
    addIcon("Hut", "Events & Places", R.drawable.type_event_and_place_hut)
    addIcon("Love Hotel", "Events & Places", R.drawable.type_event_and_place_love_hotel)
    addIcon("Wedding", "Events & Places", R.drawable.type_event_and_place_wedding)
    addIcon("Party Popper", "Events & Places", R.drawable.type_event_and_place_party_popper)
    addIcon("Confetti Ball", "Events & Places", R.drawable.type_event_and_place_confetti_ball)
    addIcon("Magic", "Events & Places", R.drawable.type_event_and_place_magic_wand)
    addIcon("Fireworks", "Events & Places", R.drawable.type_event_and_place_fireworks)
    addIcon("Firecracker", "Events & Places", R.drawable.type_event_and_place_firecracker)
    addIcon("Stadium", "Events & Places", R.drawable.type_event_and_place_stadium)
    addIcon("Bell", "Events & Places", R.drawable.type_event_and_place_bell)
    addIcon("Camping", "Events & Places", R.drawable.type_event_and_place_camping)
    addIcon("Castle", "Events & Places", R.drawable.type_event_and_place_castle)
    addIcon("Church", "Events & Places", R.drawable.type_event_and_place_church)
    addIcon("Hindu Temple", "Events & Places", R.drawable.type_event_and_place_hindu_temple)
    addIcon("Mosque", "Events & Places", R.drawable.type_event_and_place_mosque)
    addIcon("Circus", "Events & Places", R.drawable.type_event_and_place_circus_tent)
    addIcon("Cityscape", "Events & Places", R.drawable.type_event_and_place_cityscape)
    addIcon("Cityscape Dusk", "Events & Places", R.drawable.type_event_and_place_cityscape_at_dusk)
    addIcon("Convenience Store", "Events & Places", R.drawable.type_event_and_place_convenience_store)
    addIcon("Department Store", "Events & Places", R.drawable.type_event_and_place_department_store)
    addIcon("Couch Lamp", "Events & Places", R.drawable.type_event_and_place_couch_and_lamp)
    addIcon("Desert", "Events & Places", R.drawable.type_event_and_place_desert)
    addIcon("Desert Island", "Events & Places", R.drawable.type_event_and_place_desert_island)
    addIcon("Diwali Diya", "Events & Places", R.drawable.type_event_and_place_diya_lamp)
    addIcon("Christmas Tree", "Events & Places", R.drawable.type_event_and_place_christmas_tree)
    addIcon("Factory", "Events & Places", R.drawable.type_event_and_place_factory)
    addIcon("Ferris Wheel", "Events & Places", R.drawable.type_event_and_place_ferris_wheel)
    addIcon("Graduation", "Events & Places", R.drawable.type_event_and_place_graduation_cap)
    addIcon("Maracas", "Events & Places", R.drawable.type_event_and_place_maracas)
    addIcon("Milky way", "Events & Places", R.drawable.type_event_and_place_milky_way)
    addIcon("Moon Viewing", "Events & Places", R.drawable.type_event_and_place_moon_viewing_ceremony)
    addIcon("Night Stars", "Events & Places", R.drawable.type_event_and_place_night_with_stars)
    addIcon("Mount Fuji", "Events & Places", R.drawable.type_event_and_place_mount_fuji)
    addIcon("Mountain", "Events & Places", R.drawable.type_event_and_place_mountain)
    addIcon("National Park", "Events & Places", R.drawable.type_event_and_place_national_park)
    addIcon("Post Office", "Events & Places", R.drawable.type_event_and_place_post_office)
    addIcon("Snowman", "Events & Places", R.drawable.type_event_and_place_snowman)
    addIcon("Snowman without snow", "Events & Places", R.drawable.type_event_and_place_snowman_without_snow)

    // Human
    addIcon("Baby", "Human", R.drawable.type_human_baby)
    addIcon("Parents", "Human", R.drawable.type_human_parents)
    addIcon("GrandParents", "Human", R.drawable.type_human_grandparents)
    addIcon("Woman", "Human", R.drawable.type_human_woman)
    addIcon("Woman Blonde", "Human", R.drawable.type_human_woman_blonde)
    addIcon("Old Woman", "Human", R.drawable.type_human_old_woman)
    addIcon("Man", "Human", R.drawable.type_human_man)
    addIcon("Man Bald", "Human", R.drawable.type_human_man_bald)
    addIcon("Man Beard", "Human", R.drawable.type_human_man_beard)
    addIcon("Man Blonde", "Human", R.drawable.type_human_man_blonde)
    addIcon("Man Curly Hair", "Human", R.drawable.type_human_man_curly_hair)
    addIcon("Old Man", "Human", R.drawable.type_human_old_man)
    addIcon("Older Person", "Human", R.drawable.type_human_older_person)

    // Brands
    addIcon("1mg", "Brand", R.drawable.ic_brand_1mg)
    addIcon("5paisa", "Brand", R.drawable.ic_brand_5paisa)
    addIcon("99acres", "Brand", R.drawable.ic_brand_99acres)
    addIcon("abhibus", "Brand", R.drawable.ic_brand_abhibus)
    addIcon("acko", "Brand", R.drawable.ic_brand_acko)
    addIcon("adani electricity", "Brand", R.drawable.ic_brand_adani_electricity)
    addIcon("air india", "Brand", R.drawable.ic_brand_air_india)
    addIcon("airtel", "Brand", R.drawable.ic_brand_airtel)
    addIcon("ajio", "Brand", R.drawable.ic_brand_ajio)
    addIcon("amazon", "Brand", R.drawable.ic_brand_amazon)
    addIcon("amazon pay", "Brand", R.drawable.ic_brand_amazon_pay)
    addIcon("amazon prime", "Brand", R.drawable.ic_brand_amazon_prime)
    addIcon("angel one", "Brand", R.drawable.ic_brand_angel_one)
    addIcon("anytime fitness", "Brand", R.drawable.ic_brand_anytime_fitness)
    addIcon("apollo pharmacy", "Brand", R.drawable.ic_brand_apollo_pharmacy)
    addIcon("apple music", "Brand", R.drawable.ic_brand_apple_music)
    addIcon("axis", "Brand", R.drawable.ic_brand_axis_bank)
    addIcon("bajaj allianz", "Brand", R.drawable.ic_brand_bajaj_allianz)
    addIcon("bandhan bank", "Brand", R.drawable.ic_brand_bandhan_bank)
    addIcon("bank of baroda", "Brand", R.drawable.ic_brand_bank_of_baroda)
    addIcon("barbeque nation", "Brand", R.drawable.ic_brand_barbeque_nation)
    addIcon("bhim upi", "Brand", R.drawable.ic_brand_bhim)
    addIcon("bigbasket", "Brand", R.drawable.ic_brand_bigbasket)
    addIcon("blinkit", "Brand", R.drawable.ic_brand_blinkit)
    addIcon("blu smart", "Brand", R.drawable.ic_brand_blu_smart)
    addIcon("bookmyshow", "Brand", R.drawable.ic_brand_bookmyshow)
    addIcon("bounce", "Brand", R.drawable.ic_brand_bounce)
    addIcon("bses", "Brand", R.drawable.ic_brand_bses)
    addIcon("burger king", "Brand", R.drawable.ic_brand_burger_king)
    addIcon("byjus", "Brand", R.drawable.ic_brand_byjus)
    addIcon("cafe coffee day", "Brand", R.drawable.ic_brand_cafe_coffee_day)
    addIcon("canara bank", "Brand", R.drawable.ic_brand_canara_bank)
    addIcon("cinepolis", "Brand", R.drawable.ic_brand_cinepolis)
    addIcon("cleartrip", "Brand", R.drawable.ic_brand_cleartrip)
    addIcon("coin", "Brand", R.drawable.ic_brand_coin)
    addIcon("coursera", "Brand", R.drawable.ic_brand_coursera)
    addIcon("cred", "Brand", R.drawable.ic_brand_cred)
    addIcon("cultfit", "Brand", R.drawable.ic_brand_cultfit)
    addIcon("curefit", "Brand", R.drawable.ic_brand_curefit)
    addIcon("dish tv", "Brand", R.drawable.ic_brand_dish_tv)
    addIcon("adcb", "Brand", R.drawable.ic_brand_adcb)
    addIcon("airbnb", "Brand", R.drawable.ic_brand_airbnb)
    addIcon("alecu bank", "Brand", R.drawable.ic_brand_alecu_bank)
    addIcon("alinma bank", "Brand", R.drawable.ic_brand_alinma_bank)
    addIcon("al rajhi bank", "Brand", R.drawable.ic_brand_al_rajhi_bank)
    addIcon("amex", "Brand", R.drawable.ic_brand_amex)
    addIcon("apple tv", "Brand", R.drawable.ic_brand_apple_tv)
    addIcon("au bank", "Brand", R.drawable.ic_brand_au_bank)
    addIcon("bancolombia", "Brand", R.drawable.ic_brand_bancolombia)
    addIcon("bank muscat", "Brand", R.drawable.ic_brand_bank_muscat)
    addIcon("bank of india", "Brand", R.drawable.ic_brand_bank_of_india)
    addIcon("bpce bank", "Brand", R.drawable.ic_brand_bpce_bank)
    addIcon("bsnl", "Brand", R.drawable.ic_brand_bsnl)
    addIcon("bumble", "Brand", R.drawable.ic_brand_bumble)
    addIcon("cbe bank", "Brand", R.drawable.ic_brand_cbe_bank)
    addIcon("central bank of india", "Brand", R.drawable.ic_brand_central_bank_of_india)
    addIcon("charles schwab", "Brand", R.drawable.ic_brand_charles_schwab)
    addIcon("chase bank", "Brand", R.drawable.ic_brand_chase_bank)
    addIcon("chatgpt", "Brand", R.drawable.ic_brand_chatgpt)
    addIcon("cib egypt", "Brand", R.drawable.ic_brand_cib_egypt)
    addIcon("citi bank", "Brand", R.drawable.ic_brand_citi_bank)
    addIcon("city union bank", "Brand", R.drawable.ic_brand_city_union_bank)
    addIcon("claude", "Brand", R.drawable.ic_brand_claude)
    addIcon("crunchyroll", "Brand", R.drawable.ic_brand_crunchyroll)
    addIcon("dbs bank", "Brand", R.drawable.ic_brand_dbs_bank)
    addIcon("discover", "Brand", R.drawable.ic_brand_discover)
    addIcon("disney plus", "Brand", R.drawable.ic_brand_disney_plus)
    addIcon("dmart", "Brand", R.drawable.ic_brand_dmart)
    addIcon("dominos", "Brand", R.drawable.ic_brand_dominos)
    addIcon("dop", "Brand", R.drawable.ic_brand_dop)
    addIcon("dream11", "Brand", R.drawable.ic_brand_dream11)
    addIcon("dunzo", "Brand", R.drawable.ic_brand_dunzo)
    addIcon("economic times", "Brand", R.drawable.ic_brand_economic_times)
    addIcon("emirates nbd", "Brand", R.drawable.ic_brand_emirates_nbd)
    addIcon("equitas bank", "Brand", R.drawable.ic_brand_equitas_bank)
    addIcon("eros now", "Brand", R.drawable.ic_brand_eros_now)
    addIcon("etmoney", "Brand", R.drawable.ic_brand_etmoney)
    addIcon("everest bank", "Brand", R.drawable.ic_brand_everest_bank)
    addIcon("fab bank", "Brand", R.drawable.ic_brand_fab_bank)
    addIcon("fastag", "Brand", R.drawable.ic_brand_fastag)
    addIcon("federal bank", "Brand", R.drawable.ic_brand_federal_bank)
    addIcon("firstcry", "Brand", R.drawable.ic_brand_firstcry)
    addIcon("fitternity", "Brand", R.drawable.ic_brand_fitternity)
    addIcon("flipkart", "Brand", R.drawable.ic_brand_flipkart)
    addIcon("freecharge", "Brand", R.drawable.ic_brand_freecharge)
    addIcon("gaana", "Brand", R.drawable.ic_brand_gaana)
    addIcon("gemini", "Brand", R.drawable.ic_brand_gemini)
    addIcon("goibibo", "Brand", R.drawable.ic_brand_goibibo)
    addIcon("golds gym", "Brand", R.drawable.ic_brand_golds_gym)
    addIcon("google", "Brand", R.drawable.ic_brand_google)
    addIcon("google pay", "Brand", R.drawable.ic_brand_google_pay)
    addIcon("google play", "Brand", R.drawable.ic_brand_google_play)
    addIcon("great learning", "Brand", R.drawable.ic_brand_great_learning)
    addIcon("grofers", "Brand", R.drawable.ic_brand_grofers)
    addIcon("grok", "Brand", R.drawable.ic_brand_grok)
    addIcon("groww", "Brand", R.drawable.ic_brand_groww)
    addIcon("haldirams", "Brand", R.drawable.ic_brand_haldirams)
    addIcon("hathway", "Brand", R.drawable.ic_brand_hathway)
    addIcon("hbo max", "Brand", R.drawable.ic_brand_hbo_max)
    addIcon("hdfc bank", "Brand", R.drawable.ic_brand_hdfc_bank)
    addIcon("hdfc life", "Brand", R.drawable.ic_brand_hdfc_life)
    addIcon("healthifyme", "Brand", R.drawable.ic_brand_healthifyme)
    addIcon("healthkart", "Brand", R.drawable.ic_brand_healthkart)
    addIcon("hindustan times", "Brand", R.drawable.ic_brand_hindustan_times)
    addIcon("housejoy", "Brand", R.drawable.ic_brand_housejoy)
    addIcon("housingcom", "Brand", R.drawable.ic_brand_housingcom)
    addIcon("hsbc bank", "Brand", R.drawable.ic_brand_hsbc_bank)
    addIcon("huntington bank", "Brand", R.drawable.ic_brand_huntington_bank)
    addIcon("icici bank", "Brand", R.drawable.ic_brand_icici_bank)
    addIcon("icici prudential", "Brand", R.drawable.ic_brand_icici_prudential)
    addIcon("idfc first bank", "Brand", R.drawable.ic_brand_idfc_first_bank)
    addIcon("indian bank", "Brand", R.drawable.ic_brand_indian_bank)
    addIcon("indian express", "Brand", R.drawable.ic_brand_indian_express)
    addIcon("indian overseas bank", "Brand", R.drawable.ic_brand_indian_overseas_bank)
    addIcon("indigo", "Brand", R.drawable.ic_brand_indigo)
    addIcon("ind money", "Brand", R.drawable.ic_brand_ind_money)
    addIcon("indusind bank", "Brand", R.drawable.ic_brand_indusind_bank)
    addIcon("inox", "Brand", R.drawable.ic_brand_inox)
    addIcon("ippb", "Brand", R.drawable.ic_brand_ippb)
    addIcon("irctc", "Brand", R.drawable.ic_brand_irctc)
    addIcon("ixigo", "Brand", R.drawable.ic_brand_ixigo)
    addIcon("jio", "Brand", R.drawable.ic_brand_jio)
    addIcon("jiocinema", "Brand", R.drawable.ic_brand_jiocinema)
    addIcon("jiohotstar", "Brand", R.drawable.ic_brand_jiohotstar)
    addIcon("jiomart", "Brand", R.drawable.ic_brand_jiomart)
    addIcon("jiosaavn", "Brand", R.drawable.ic_brand_jiosaavn)
    addIcon("jupiter bank", "Brand", R.drawable.ic_brand_jupiter_bank)
    addIcon("juspay", "Brand", R.drawable.ic_brand_juspay)
    addIcon("karnataka bank", "Brand", R.drawable.ic_brand_karnataka_bank)
    addIcon("kerala gramin bank", "Brand", R.drawable.ic_brand_kerala_gramin_bank)
    addIcon("kfc", "Brand", R.drawable.ic_brand_kfc)
    addIcon("kotak mahindra bank", "Brand", R.drawable.ic_brand_kotak_mahindra_bank)
    addIcon("kuvera", "Brand", R.drawable.ic_brand_kuvera)
    addIcon("laxmi sunrise bank", "Brand", R.drawable.ic_brand_laxmi_sunrise_bank)
    addIcon("lazypay", "Brand", R.drawable.ic_brand_lazypay)
    addIcon("lic", "Brand", R.drawable.ic_brand_lic)
    addIcon("liv bank", "Brand", R.drawable.ic_brand_liv_bank)
    addIcon("lybrate", "Brand", R.drawable.ic_brand_lybrate)
    addIcon("magicbricks", "Brand", R.drawable.ic_brand_magicbricks)
    addIcon("makemytrip", "Brand", R.drawable.ic_brand_makemytrip)
    addIcon("manjushree finance", "Brand", R.drawable.ic_brand_manjushree_finance)
    addIcon("mashreq bank", "Brand", R.drawable.ic_brand_mashreq_bank)
    addIcon("max life", "Brand", R.drawable.ic_brand_max_life)
    addIcon("mbank cz", "Brand", R.drawable.ic_brand_mbank_cz)
    addIcon("mcdonalds", "Brand", R.drawable.ic_brand_mcdonalds)
    addIcon("medlife", "Brand", R.drawable.ic_brand_medlife)
    addIcon("meesho", "Brand", R.drawable.ic_brand_meesho)
    addIcon("microsoft teams", "Brand", R.drawable.ic_brand_microsoft_teams)
    addIcon("mobikwik", "Brand", R.drawable.ic_brand_mobikwik)
    addIcon("mpesa", "Brand", R.drawable.ic_brand_mpesa)
    addIcon("mpl", "Brand", R.drawable.ic_brand_mpl)
    addIcon("mtnl", "Brand", R.drawable.ic_brand_mtnl)
    addIcon("mx player", "Brand", R.drawable.ic_brand_mx_player)
    addIcon("myntra", "Brand", R.drawable.ic_brand_myntra)
    addIcon("nabil bank", "Brand", R.drawable.ic_brand_nabil_bank)
    addIcon("navy federal", "Brand", R.drawable.ic_brand_navy_federal)
    addIcon("netflix", "Brand", R.drawable.ic_brand_netflix)
    addIcon("netmeds", "Brand", R.drawable.ic_brand_netmeds)
    addIcon("nmb bank", "Brand", R.drawable.ic_brand_nmb_bank)
    addIcon("nobroker", "Brand", R.drawable.ic_brand_nobroker)
    addIcon("nykaa", "Brand", R.drawable.ic_brand_nykaa)
    addIcon("ola", "Brand", R.drawable.ic_brand_ola)
    addIcon("ola electric", "Brand", R.drawable.ic_brand_ola_electric)
    addIcon("oyo", "Brand", R.drawable.ic_brand_oyo)
    addIcon("paytm", "Brand", R.drawable.ic_brand_paytm)
    addIcon("paytm first games", "Brand", R.drawable.ic_brand_paytm_first_games)
    addIcon("paytm insider", "Brand", R.drawable.ic_brand_paytm_insider)
    addIcon("paytm money", "Brand", R.drawable.ic_brand_paytm_money)
    addIcon("pepperfry", "Brand", R.drawable.ic_brand_pepperfry)
    addIcon("perplexity", "Brand", R.drawable.ic_brand_perplexity)
    addIcon("pharmeasy", "Brand", R.drawable.ic_brand_pharmeasy)
    addIcon("phonepe", "Brand", R.drawable.ic_brand_phonepe)
    addIcon("pizza hut", "Brand", R.drawable.ic_brand_pizza_hut)
    addIcon("playstation", "Brand", R.drawable.ic_brand_playstation)
    addIcon("policybazaar", "Brand", R.drawable.ic_brand_policybazaar)
    addIcon("practo", "Brand", R.drawable.ic_brand_practo)
    addIcon("prime commercial bank", "Brand", R.drawable.ic_brand_prime_commercial_bank)
    addIcon("priorbank", "Brand", R.drawable.ic_brand_priorbank)
    addIcon("proton vpn", "Brand", R.drawable.ic_brand_proton_vpn)
    addIcon("punjab national bank", "Brand", R.drawable.ic_brand_punjab_national_bank)
    addIcon("pvr", "Brand", R.drawable.ic_brand_pvr)
    addIcon("rapido", "Brand", R.drawable.ic_brand_rapido)
    addIcon("rbl bank", "Brand", R.drawable.ic_brand_rbl_bank)
    addIcon("redbus", "Brand", R.drawable.ic_brand_redbus)
    addIcon("saraswat bank", "Brand", R.drawable.ic_brand_saraswat_bank)
    addIcon("sbi", "Brand", R.drawable.ic_brand_sbi)
    addIcon("sbi life", "Brand", R.drawable.ic_brand_sbi_life)
    addIcon("selcom pesa", "Brand", R.drawable.ic_brand_selcom_pesa)
    addIcon("shopclues", "Brand", R.drawable.ic_brand_shopclues)
    addIcon("siddhartha bank", "Brand", R.drawable.ic_brand_siddhartha_bank)
    addIcon("simpl", "Brand", R.drawable.ic_brand_simpl)
    addIcon("simplilearn", "Brand", R.drawable.ic_brand_simplilearn)
    addIcon("slack", "Brand", R.drawable.ic_brand_slack)
    addIcon("slice", "Brand", R.drawable.ic_brand_slice)
    addIcon("smallcase", "Brand", R.drawable.ic_brand_smallcase)
    addIcon("snapdeal", "Brand", R.drawable.ic_brand_snapdeal)
    addIcon("sony liv", "Brand", R.drawable.ic_brand_sony_liv)
    addIcon("south indian bank", "Brand", R.drawable.ic_brand_south_indian_bank)
    addIcon("spicejet", "Brand", R.drawable.ic_brand_spicejet)
    addIcon("spotify", "Brand", R.drawable.ic_brand_spotify)
    addIcon("standard chartered", "Brand", R.drawable.ic_brand_standard_chartered)
    addIcon("starbucks", "Brand", R.drawable.ic_brand_starbucks)
    addIcon("sun direct", "Brand", R.drawable.ic_brand_sun_direct)
    addIcon("swiggy", "Brand", R.drawable.ic_brand_swiggy)
    addIcon("tata aia", "Brand", R.drawable.ic_brand_tata_aia)
    addIcon("tata power", "Brand", R.drawable.ic_brand_tata_power)
    addIcon("tata sky", "Brand", R.drawable.ic_brand_tata_sky)
    addIcon("tbank", "Brand", R.drawable.ic_brand_tbank)
    addIcon("telebirr", "Brand", R.drawable.ic_brand_telebirr)
    addIcon("the hindu", "Brand", R.drawable.ic_brand_the_hindu)
    addIcon("the times of india", "Brand", R.drawable.ic_brand_the_times_of_india)
    addIcon("tigo pesa", "Brand", R.drawable.ic_brand_tigo_pesa)
    addIcon("tikona", "Brand", R.drawable.ic_brand_tikona)
    addIcon("timespoints", "Brand", R.drawable.ic_brand_timespoints)
    addIcon("toppr", "Brand", R.drawable.ic_brand_toppr)
    addIcon("truemeds", "Brand", R.drawable.ic_brand_truemeds)
    addIcon("uber", "Brand", R.drawable.ic_brand_uber)
    addIcon("udacity", "Brand", R.drawable.ic_brand_udacity)
    addIcon("udemy", "Brand", R.drawable.ic_brand_udemy)
    addIcon("unacademy", "Brand", R.drawable.ic_brand_unacademy)
    addIcon("union bank", "Brand", R.drawable.ic_brand_union_bank)
    addIcon("upgrad", "Brand", R.drawable.ic_brand_upgrad)
    addIcon("upstox", "Brand", R.drawable.ic_brand_upstox)
    addIcon("urban company", "Brand", R.drawable.ic_brand_urban_company)
    addIcon("urban ladder", "Brand", R.drawable.ic_brand_urban_ladder)
    addIcon("vedantu", "Brand", R.drawable.ic_brand_vedantu)
    addIcon("visa", "Brand", R.drawable.ic_brand_visa)
    addIcon("vistara", "Brand", R.drawable.ic_brand_vistara)
    addIcon("vodafone", "Brand", R.drawable.ic_brand_vodafone)
    addIcon("vodafone idea", "Brand", R.drawable.ic_brand_vodafone_idea)
    addIcon("vogo", "Brand", R.drawable.ic_brand_vogo)
    addIcon("voot", "Brand", R.drawable.ic_brand_voot)
    addIcon("whitehat jr", "Brand", R.drawable.ic_brand_whitehat_jr)
    addIcon("wynk", "Brand", R.drawable.ic_brand_wynk)
    addIcon("xbox", "Brand", R.drawable.ic_brand_xbox)
    addIcon("x", "Brand", R.drawable.ic_brand_x)
    addIcon("yatra", "Brand", R.drawable.ic_brand_yatra)
    addIcon("yes bank", "Brand", R.drawable.ic_brand_yes_bank)
    addIcon("youtube", "Brand", R.drawable.ic_brand_youtube)
    addIcon("youtube music", "Brand", R.drawable.ic_brand_youtube_music)
    addIcon("yulu", "Brand", R.drawable.ic_brand_yulu)
    addIcon("zee5", "Brand", R.drawable.ic_brand_zee5)
    addIcon("zepto", "Brand", R.drawable.ic_brand_zepto)
    addIcon("zerodha", "Brand", R.drawable.ic_brand_zerodha)
    addIcon("zomato", "Brand", R.drawable.ic_brand_zomato)
    addIcon("zoom", "Brand", R.drawable.ic_brand_zoom)
    addIcon("abu dhabi commercial bank", "Brand", R.drawable.ic_brand_adcb)
    addIcon("access bank", "Brand", R.drawable.ic_brand_access_bank)
    addIcon("adelfi", "Brand", R.drawable.ic_brand_adelfi)
    addIcon("altana fcu", "Brand", R.drawable.ic_brand_altana_fcu)
    addIcon("arab bank", "Brand", R.drawable.ic_brand_arab_bank)
    addIcon("baac", "Brand", R.drawable.ic_brand_baac)
    addIcon("bangkok bank", "Brand", R.drawable.ic_brand_bangkok_bank)
    addIcon("bankino", "Brand", R.drawable.ic_brand_bankino)
    addIcon("blu bank", "Brand", R.drawable.ic_brand_blu_bank)
    addIcon("cashfree", "Brand", R.drawable.ic_brand_cashfree)
    addIcon("cimb thai", "Brand", R.drawable.ic_brand_cimb)
    addIcon("crdb bank", "Brand", R.drawable.ic_brand_crdb)
    addIcon("dashen bank", "Brand", R.drawable.ic_brand_dashen_bank)
    addIcon("dhanlaxmi bank", "Brand", R.drawable.ic_brand_dhanlaxmi_bank)
    addIcon("diamond trust bank", "Brand", R.drawable.ic_brand_diamond_trust)
    addIcon("e-mola", "Brand", R.drawable.ic_brand_e_mola)
    addIcon("emirates islamic", "Brand", R.drawable.ic_brand_emirates_islamic)
    addIcon("enpara bank", "Brand", R.drawable.ic_brand_enpara)
    addIcon("faysal bank", "Brand", R.drawable.ic_brand_faysal_bank)
    addIcon("greater bank", "Brand", R.drawable.ic_brand_greater_bank)
    addIcon("government savings bank", "Brand", R.drawable.ic_brand_gsb)
    addIcon("hdfc mutual fund", "Brand", R.drawable.ic_brand_hdfc_mutual_funds)
    addIcon("idbi bank", "Brand", R.drawable.ic_brand_idbi)
    addIcon("jaiz bank", "Brand", R.drawable.ic_brand_jaiz_bank)
    addIcon("jk bank", "Brand", R.drawable.ic_brand_jk_bank)
    addIcon("kasikorn bank", "Brand", R.drawable.ic_brand_kasikorn_bank)
    addIcon("kerala bank", "Brand", R.drawable.ic_brand_kerela_bank)
    addIcon("keystone bank", "Brand", R.drawable.ic_brand_keystone_bank)
    addIcon("kotak bank", "Brand", R.drawable.ic_brand_kotak_mahindra_bank)
    addIcon("krungsri", "Brand", R.drawable.ic_brand_krungsri)
    addIcon("krungthai bank", "Brand", R.drawable.ic_brand_krungthai)
    addIcon("ktc", "Brand", R.drawable.ic_brand_ktc)
    addIcon("mellat bank", "Brand", R.drawable.ic_brand_mellat_bank)
    addIcon("melli bank", "Brand", R.drawable.ic_brand_melli_bank)
    addIcon("millennium bim", "Brand", R.drawable.ic_brand_millennium_bim)
    addIcon("mixx by yas", "Brand", R.drawable.ic_brand_mixx_by_yas)
    addIcon("navi mutual fund", "Brand", R.drawable.ic_brand_navi_mutual_funds)
    addIcon("nmb tanzania", "Brand", R.drawable.ic_brand_nmb_tanzania)
    addIcon("nsdl payments bank", "Brand", R.drawable.ic_brand_nsdl_payments_bank)
    addIcon("old hickory", "Brand", R.drawable.ic_brand_old_hickory)
    addIcon("onecard", "Brand", R.drawable.ic_brand_onecard)
    addIcon("opay", "Brand", R.drawable.ic_brand_0pay)
    addIcon("parsian bank", "Brand", R.drawable.ic_brand_parsian_bank)
    addIcon("punjab & sind bank", "Brand", R.drawable.ic_brand_punjab_sind_bank)
    addIcon("sabb bank", "Brand", R.drawable.ic_brand_sabb_bank)
    addIcon("sampath bank", "Brand", R.drawable.ic_brand_sampath_bank)
    addIcon("siam commercial bank", "Brand", R.drawable.ic_brand_siam_commercial)
    addIcon("snb al ahli", "Brand", R.drawable.ic_brand_snb_alahli)
    addIcon("sparkasse", "Brand", R.drawable.ic_brand_sparkasse_rhein_maas)
    addIcon("standard bank mozambique", "Brand", R.drawable.ic_brand_standard_bank)
    addIcon("stc bank", "Brand", R.drawable.ic_brand_stc_bank)
    addIcon("ttb", "Brand", R.drawable.ic_brand_ttb)
    addIcon("uco bank", "Brand", R.drawable.ic_brand_uco_bank)
    addIcon("uob thailand", "Brand", R.drawable.ic_brand_uob)
    addIcon("utkarsh bank", "Brand", R.drawable.ic_brand_utkarsh_bank)
    addIcon("zemen bank", "Brand", R.drawable.ic_brand_zemen_bank)
    addIcon("zenith bank", "Brand", R.drawable.ic_brand_zenith_bank)

    return icons
}
