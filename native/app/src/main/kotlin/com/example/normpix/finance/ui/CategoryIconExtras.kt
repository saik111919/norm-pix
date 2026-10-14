package com.example.normpix.finance.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AirplaneTicket
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.BabyChangingStation
import androidx.compose.material.icons.outlined.Backpack
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.BakeryDining
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Cabin
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CarRepair
import androidx.compose.material.icons.outlined.CardMembership
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.Church
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.CurrencyBitcoin
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsRailway
import androidx.compose.material.icons.outlined.DirectionsSubway
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Egg
import androidx.compose.material.icons.outlined.Elderly
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.EmojiFoodBeverage
import androidx.compose.material.icons.outlined.EvStation
import androidx.compose.material.icons.outlined.Face2
import androidx.compose.material.icons.outlined.FaceRetouchingNatural
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.HotTub
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.Icecream
import androidx.compose.material.icons.outlined.Interests
import androidx.compose.material.icons.outlined.KebabDining
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.LocalAtm
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalCarWash
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.LocalPizza
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.MedicalInformation
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.Money
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Plumbing
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SetMeal
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.SportsBar
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.TakeoutDining
import androidx.compose.material.icons.outlined.TempleHindu
import androidx.compose.material.icons.outlined.Theaters
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material.icons.outlined.Toys
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WineBar
import androidx.compose.material.icons.outlined.Yard

/**
 * Extra icon choices for the category icon picker, beyond the core ones in [categoryIcons]; merged by CategoryVisuals.
 * Outlined like the core set (stocks uses the mirrored chart, as the core "Investments" does), so the two read as one family.
 */
internal val extraCategoryIcons: List<Pair<String, CategoryIconSpec>> = listOf(
    // Food & drink
    "pizza" to CategoryIconSpec(Icons.Outlined.LocalPizza, "Pizza", "🍕"),
    "fast_food" to CategoryIconSpec(Icons.Outlined.Fastfood, "Fast Food", "🍔"),
    "bakery" to CategoryIconSpec(Icons.Outlined.BakeryDining, "Bakery", "🥐"),
    "ice_cream" to CategoryIconSpec(Icons.Outlined.Icecream, "Ice Cream", "🍦"),
    "bar" to CategoryIconSpec(Icons.Outlined.LocalBar, "Bar", "🍸"),
    "wine" to CategoryIconSpec(Icons.Outlined.WineBar, "Wine", "🍷"),
    "beer" to CategoryIconSpec(Icons.Outlined.SportsBar, "Beer", "🍺"),
    "tea" to CategoryIconSpec(Icons.Outlined.EmojiFoodBeverage, "Tea", "🍵"),
    "takeaway" to CategoryIconSpec(Icons.Outlined.TakeoutDining, "Takeaway", "🥡"),
    "delivery" to CategoryIconSpec(Icons.Outlined.DeliveryDining, "Delivery", "🛵"),
    "fruit_veg" to CategoryIconSpec(Icons.Outlined.Eco, "Fruit & Veg", "🥦"),
    "meat" to CategoryIconSpec(Icons.Outlined.KebabDining, "Meat", "🍖"),
    "seafood" to CategoryIconSpec(Icons.Outlined.SetMeal, "Seafood", "🦐"),
    "snacks" to CategoryIconSpec(Icons.Outlined.Cookie, "Snacks", "🍿"),
    "dessert" to CategoryIconSpec(Icons.Outlined.Cake, "Dessert", "🍰"),
    "dairy" to CategoryIconSpec(Icons.Outlined.Egg, "Dairy", "🥚"),

    // Transport
    "taxi" to CategoryIconSpec(Icons.Outlined.LocalTaxi, "Taxi", "🚕"),
    "bike" to CategoryIconSpec(Icons.Outlined.DirectionsBike, "Bike", "🚲"),
    "scooter" to CategoryIconSpec(Icons.Outlined.TwoWheeler, "Scooter", "🛴"),
    "parking" to CategoryIconSpec(Icons.Outlined.LocalParking, "Parking", "🅿️"),
    "toll" to CategoryIconSpec(Icons.Outlined.Toll, "Toll", "🚧"),
    "flight" to CategoryIconSpec(Icons.Outlined.AirplaneTicket, "Flight", "🛫"),
    "train" to CategoryIconSpec(Icons.Outlined.DirectionsRailway, "Train", "🚆"),
    "bus" to CategoryIconSpec(Icons.Outlined.DirectionsBus, "Bus", "🚌"),
    "metro" to CategoryIconSpec(Icons.Outlined.DirectionsSubway, "Metro", "🚇"),
    "car_repair" to CategoryIconSpec(Icons.Outlined.CarRepair, "Car Repair", "🔧"),
    "car_wash" to CategoryIconSpec(Icons.Outlined.LocalCarWash, "Car Wash", "🧼"),
    "ev_charging" to CategoryIconSpec(Icons.Outlined.EvStation, "EV Charging", "🔌"),

    // Home
    "rent" to CategoryIconSpec(Icons.Outlined.Apartment, "Rent", "🏢"),
    "furniture" to CategoryIconSpec(Icons.Outlined.Chair, "Furniture", "🪑"),
    "repairs" to CategoryIconSpec(Icons.Outlined.Handyman, "Repairs", "🔨"),
    "maintenance" to CategoryIconSpec(Icons.Outlined.Construction, "Maintenance", "🧰"),
    "cleaning" to CategoryIconSpec(Icons.Outlined.CleaningServices, "Cleaning", "🧹"),
    "garden" to CategoryIconSpec(Icons.Outlined.Yard, "Garden", "🌻"),
    "plants" to CategoryIconSpec(Icons.Outlined.LocalFlorist, "Plants", "🌱"),
    "electricity" to CategoryIconSpec(Icons.Outlined.ElectricBolt, "Electricity", "💡"),
    "water" to CategoryIconSpec(Icons.Outlined.WaterDrop, "Water", "💧"),
    "gas" to CategoryIconSpec(Icons.Outlined.LocalFireDepartment, "Gas", "🔥"),
    "laundry" to CategoryIconSpec(Icons.Outlined.LocalLaundryService, "Laundry", "🧺"),
    "appliances" to CategoryIconSpec(Icons.Outlined.Kitchen, "Appliances", "🧊"),
    "security" to CategoryIconSpec(Icons.Outlined.Security, "Security", "🔒"),
    "plumbing" to CategoryIconSpec(Icons.Outlined.Plumbing, "Plumbing", "🚽"),

    // Health & beauty
    "doctor" to CategoryIconSpec(Icons.Outlined.MedicalInformation, "Doctor", "🩺"),
    "dentist" to CategoryIconSpec(Icons.Outlined.Healing, "Dentist", "🦷"),
    "pharmacy" to CategoryIconSpec(Icons.Outlined.LocalPharmacy, "Pharmacy", "💊"),
    "hospital" to CategoryIconSpec(Icons.Outlined.LocalHospital, "Hospital", "🏥"),
    "eye_care" to CategoryIconSpec(Icons.Outlined.RemoveRedEye, "Eye Care", "👁️"),
    "vision" to CategoryIconSpec(Icons.Outlined.Visibility, "Vision", "👓"),
    "therapy" to CategoryIconSpec(Icons.Outlined.Psychology, "Therapy", "🧠"),
    "spa" to CategoryIconSpec(Icons.Outlined.HotTub, "Spa", "🛁"),
    "haircut" to CategoryIconSpec(Icons.Outlined.ContentCut, "Haircut", "💇"),
    "beauty" to CategoryIconSpec(Icons.Outlined.Face2, "Beauty", "💄"),
    "skincare" to CategoryIconSpec(Icons.Outlined.FaceRetouchingNatural, "Skincare", "🧴"),

    // Fun
    "movies" to CategoryIconSpec(Icons.Outlined.Theaters, "Movies", "🎬"),
    "music" to CategoryIconSpec(Icons.Outlined.MusicNote, "Music", "🎵"),
    "games" to CategoryIconSpec(Icons.Outlined.SportsEsports, "Games", "🎮"),
    "books" to CategoryIconSpec(Icons.Outlined.MenuBook, "Books", "📚"),
    "sports" to CategoryIconSpec(Icons.Outlined.SportsSoccer, "Sports", "⚽"),
    "concerts" to CategoryIconSpec(Icons.Outlined.Mic, "Concerts", "🎤"),
    "tickets" to CategoryIconSpec(Icons.Outlined.ConfirmationNumber, "Event Tickets", "🎟️"),
    "hobbies" to CategoryIconSpec(Icons.Outlined.Interests, "Hobbies", "🧩"),
    "art" to CategoryIconSpec(Icons.Outlined.Palette, "Art", "🎨"),
    "camping" to CategoryIconSpec(Icons.Outlined.Cabin, "Camping", "⛺"),
    "party" to CategoryIconSpec(Icons.Outlined.Celebration, "Party", "🎉"),
    "streaming" to CategoryIconSpec(Icons.Outlined.LiveTv, "Streaming", "📺"),

    // Tech
    "software" to CategoryIconSpec(Icons.Outlined.Code, "Software", "💾"),
    "cloud" to CategoryIconSpec(Icons.Outlined.Cloud, "Cloud", "☁️"),
    "hosting" to CategoryIconSpec(Icons.Outlined.Dns, "Hosting", "📡"),
    "domain" to CategoryIconSpec(Icons.Outlined.Language, "Domain", "🌐"),
    "gadgets" to CategoryIconSpec(Icons.Outlined.Watch, "Gadgets", "⌚"),
    "laptop" to CategoryIconSpec(Icons.Outlined.Laptop, "Laptop", "💻"),
    "mobile_data" to CategoryIconSpec(Icons.Outlined.SignalCellularAlt, "Mobile Data", "📶"),
    "ai" to CategoryIconSpec(Icons.Outlined.Psychology, "AI", "🤖"),
    "vpn" to CategoryIconSpec(Icons.Outlined.Security, "VPN", "🔒"),

    // Kids & family
    "baby" to CategoryIconSpec(Icons.Outlined.BabyChangingStation, "Baby", "👶"),
    "school_fees" to CategoryIconSpec(Icons.Outlined.Backpack, "School Fees", "🎒"),
    "toys" to CategoryIconSpec(Icons.Outlined.Toys, "Toys", "🧸"),
    "childcare" to CategoryIconSpec(Icons.Outlined.FamilyRestroom, "Childcare", "🍼"),
    "wedding" to CategoryIconSpec(Icons.Outlined.Church, "Wedding", "💍"),
    "family" to CategoryIconSpec(Icons.Outlined.Groups, "Family", "🫂"),

    // Money
    "bank_fees" to CategoryIconSpec(Icons.Outlined.Receipt, "Bank Fees", "🏦"),
    "savings" to CategoryIconSpec(Icons.Outlined.Savings, "Savings", "🐷"),
    "credit_card" to CategoryIconSpec(Icons.Outlined.CreditCard, "Credit Card", "💳"),
    "atm" to CategoryIconSpec(Icons.Outlined.LocalAtm, "ATM", "🏧"),
    "cash" to CategoryIconSpec(Icons.Outlined.Money, "Cash", "💵"),
    "dividend" to CategoryIconSpec(Icons.Outlined.Paid, "Dividend", "💰"),
    "rental_income" to CategoryIconSpec(Icons.Outlined.Key, "Rental Income", "🔑"),
    "bonus" to CategoryIconSpec(Icons.Outlined.EmojiEvents, "Bonus", "🏆"),
    "freelance" to CategoryIconSpec(Icons.Outlined.Computer, "Freelance", "💼"),
    "side_hustle" to CategoryIconSpec(Icons.Outlined.RocketLaunch, "Side Hustle", "🚀"),
    "pension" to CategoryIconSpec(Icons.Outlined.Elderly, "Pension", "👵"),
    "mutual_funds" to CategoryIconSpec(Icons.Outlined.PieChart, "Mutual Funds", "📊"),
    "stocks" to CategoryIconSpec(Icons.AutoMirrored.Outlined.ShowChart, "Stocks", "📈"),
    "crypto" to CategoryIconSpec(Icons.Outlined.CurrencyBitcoin, "Crypto", "🪙"),
    "gold" to CategoryIconSpec(Icons.Outlined.MilitaryTech, "Gold", "🥇"),
    "jewelry" to CategoryIconSpec(Icons.Outlined.Diamond, "Jewelry", "💎"),

    // Misc
    "donation" to CategoryIconSpec(Icons.Outlined.Handshake, "Donation", "🙏"),
    "temple" to CategoryIconSpec(Icons.Outlined.TempleHindu, "Temple", "🛕"),
    "hotel" to CategoryIconSpec(Icons.Outlined.Hotel, "Hotel", "🏨"),
    "visa" to CategoryIconSpec(Icons.Outlined.Badge, "Visa", "🛂"),
    "luggage" to CategoryIconSpec(Icons.Outlined.Luggage, "Luggage", "🧳"),
    "shopping_bags" to CategoryIconSpec(Icons.Outlined.ShoppingBasket, "Shopping Bags", "🛍️"),
    "cosmetics" to CategoryIconSpec(Icons.Outlined.Brush, "Cosmetics", "💅"),
    "stationery" to CategoryIconSpec(Icons.Outlined.EditNote, "Stationery", "📝"),
    "courier" to CategoryIconSpec(Icons.Outlined.LocalShipping, "Courier", "📦"),
    "legal" to CategoryIconSpec(Icons.Outlined.Gavel, "Legal", "📜"),
    "fines" to CategoryIconSpec(Icons.Outlined.ReportProblem, "Fines", "🚨"),
    "membership" to CategoryIconSpec(Icons.Outlined.CardMembership, "Membership", "🔖"),
    "vet" to CategoryIconSpec(Icons.Outlined.Pets, "Vet", "🐕"),
)
