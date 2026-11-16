package com.sonzzk.claudebite.data.food

/**
 * A small built-in food list (per 100 g, USDA-style averages) so search works offline and costs zero tokens.
 * Format: name | kcal | protein | carbs | fat | fiber | sugar | serving grams | serving label
 */
object CommonFoods {
    private const val RAW = """
Apple|52|0.3|14|0.2|2.4|10|182|1 medium
Banana|89|1.1|23|0.3|2.6|12|118|1 medium
Orange|47|0.9|12|0.1|2.4|9|131|1 medium
Strawberries|32|0.7|7.7|0.3|2|4.9|150|1 cup
Blueberries|57|0.7|14|0.3|2.4|10|148|1 cup
Grapes|69|0.7|18|0.2|0.9|16|150|1 cup
Avocado|160|2|8.5|15|6.7|0.7|150|1 avocado
Watermelon|30|0.6|7.6|0.2|0.4|6.2|280|1 wedge
Mango|60|0.8|15|0.4|1.6|14|165|1 cup
Broccoli|34|2.8|7|0.4|2.6|1.7|90|1 cup
Spinach|23|2.9|3.6|0.4|2.2|0.4|30|1 cup raw
Carrot|41|0.9|10|0.2|2.8|4.7|61|1 medium
Tomato|18|0.9|3.9|0.2|1.2|2.6|123|1 medium
Cucumber|15|0.7|3.6|0.1|0.5|1.7|100|1/3 cucumber
Bell pepper|31|1|6|0.3|2.1|4.2|120|1 medium
Onion|40|1.1|9.3|0.1|1.7|4.2|110|1 medium
Sweet potato, baked|90|2|21|0.2|3.3|6.5|150|1 medium
Potato, boiled|87|1.9|20|0.1|1.8|0.9|170|1 medium
French fries|312|3.4|41|15|3.8|0.3|117|1 medium serving
Mixed salad greens|17|1.4|3.3|0.2|2|1.2|85|1 bowl
White rice, cooked|130|2.7|28|0.3|0.4|0.1|158|1 cup
Brown rice, cooked|123|2.7|26|1|1.6|0.4|195|1 cup
Pasta, cooked|158|5.8|31|0.9|1.8|0.6|140|1 cup
Oats, dry|389|17|66|6.9|10.6|1|40|1/2 cup
Oatmeal, cooked with water|71|2.5|12|1.5|1.7|0.3|234|1 bowl
Bread, white|265|9|49|3.2|2.7|5|30|1 slice
Bread, whole wheat|247|13|41|3.4|7|6|32|1 slice
Bagel|250|10|49|1.5|2.1|6|105|1 bagel
Tortilla, flour|306|8|50|8|3.5|3.5|45|1 tortilla
Quinoa, cooked|120|4.4|21|1.9|2.8|0.9|185|1 cup
Granola|471|10|64|20|5.3|24|60|1/2 cup
Cornflakes|357|7.5|84|0.4|3.3|9.5|30|1 bowl
Egg|143|13|0.7|9.5|0|0.4|50|1 large
Egg white|52|11|0.7|0.2|0|0.7|33|1 large
Chicken breast, cooked|165|31|0|3.6|0|0|120|1 breast
Chicken thigh, cooked|209|26|0|11|0|0|110|1 thigh
Ground beef 10%, cooked|217|26|0|12|0|0|100|100 g
Steak, sirloin, cooked|206|29|0|9|0|0|170|1 steak
Pork chop, cooked|231|26|0|14|0|0|150|1 chop
Bacon|541|37|1.4|42|0|0|16|2 slices
Ham|145|21|1.5|6|0|1.2|56|2 slices
Salmon, cooked|206|22|0|12|0|0|150|1 fillet
Tuna, canned in water|116|26|0|0.8|0|0|140|1 can
Shrimp, cooked|99|24|0.2|0.3|0|0|85|3 oz
Tofu, firm|144|17|2.8|8.7|2.3|0.6|126|1/2 block
Tempeh|192|20|7.6|11|0|0|100|100 g
Lentils, cooked|116|9|20|0.4|7.9|1.8|198|1 cup
Chickpeas, cooked|164|8.9|27|2.6|7.6|4.8|164|1 cup
Black beans, cooked|132|8.9|24|0.5|8.7|0.3|172|1 cup
Edamame|121|12|8.9|5.2|5.2|2.2|155|1 cup
Milk, whole|61|3.2|4.8|3.3|0|5|244|1 cup
Milk, skim|34|3.4|5|0.1|0|5|245|1 cup
Oat milk|46|1|6.7|1.5|0.8|4|240|1 cup
Greek yogurt, plain 2%|73|10|3.9|1.9|0|3.6|170|1 cup
Greek yogurt, nonfat|59|10|3.6|0.4|0|3.2|170|1 cup
Yogurt, fruit|99|4|18|1.4|0|17|150|1 pot
Cottage cheese|98|11|3.4|4.3|0|2.7|113|1/2 cup
Cheddar cheese|403|25|1.3|33|0|0.5|28|1 slice
Mozzarella|280|28|3.1|17|0|1|28|1 oz
Butter|717|0.9|0.1|81|0|0.1|14|1 tbsp
Olive oil|884|0|0|100|0|0|13.5|1 tbsp
Peanut butter|588|25|20|50|6|9|32|2 tbsp
Almonds|579|21|22|50|12.5|4.4|28|1 handful
Walnuts|654|15|14|65|6.7|2.6|28|1 handful
Mixed nuts|607|20|21|54|7|4|30|1 handful
Hummus|166|7.9|14|9.6|6|0.3|30|2 tbsp
Whey protein shake|370|80|7|5|0|4|30|1 scoop
Protein bar|350|30|40|9|8|5|60|1 bar
Pizza, cheese|266|11|33|10|2.3|3.6|107|1 slice
Burger, single patty|254|13|30|9|1.2|6|180|1 burger
Hot dog|290|10|24|17|0.8|4|100|1 hot dog
Sushi roll (salmon)|150|6|25|2.5|0.5|5|200|8 pieces
Burrito, bean & cheese|206|8|29|6.6|3.5|1.5|220|1 burrito
Ramen, instant (prepared)|188|4.5|27|7|1|0.6|300|1 bowl
Fried rice|163|4|24|5.5|1|0.5|200|1 plate
Chicken curry with rice|165|9|20|5|1.2|2|350|1 plate
Spaghetti bolognese|132|7|16|4.5|1.5|2.5|350|1 plate
Caesar salad|127|4.5|6|10|1.5|1.5|200|1 bowl
Kebab / gyro|215|12|19|10|1.5|2|250|1 wrap
Pancakes|227|6.4|28|10|0.9|7|77|2 pancakes
Waffle|291|7.9|33|14|1.7|5|75|1 waffle
Croissant|406|8.2|46|21|2.6|11|57|1 croissant
Chocolate, milk|535|7.7|59|30|3.4|52|25|1/4 bar
Chocolate, dark 70%|598|7.8|46|43|11|24|25|1/4 bar
Ice cream, vanilla|207|3.5|24|11|0.7|21|66|1 scoop
Cookie, chocolate chip|488|5|64|24|2|35|30|2 cookies
Donut, glazed|421|5|51|22|1.2|26|60|1 donut
Chips / crisps|536|7|53|35|4.8|0.3|28|1 small bag
Popcorn, air-popped|387|13|78|4.5|15|0.9|8|1 cup
Cola|42|0|10.6|0|0|10.6|330|1 can
Orange juice|45|0.7|10|0.2|0.2|8.4|248|1 glass
Coffee, black|1|0.1|0|0|0|0|240|1 cup
Latte|54|3.4|5.3|2|0|5|350|1 grande
Boba milk tea|90|0.8|17|2.2|0|11|500|1 cup
Beer|43|0.5|3.6|0|0|0|330|1 bottle
Wine, red|85|0.1|2.6|0|0|0.6|150|1 glass
Smoothie, fruit|60|0.8|14|0.2|1.2|11|350|1 bottle
"""

    val all: List<FoodProduct> by lazy {
        RAW.trim().lines().mapNotNull { line ->
            val p = line.split('|')
            if (p.size < 9) return@mapNotNull null
            FoodProduct(
                name = p[0],
                kcal100 = p[1].toFloat(), protein100 = p[2].toFloat(), carbs100 = p[3].toFloat(), fat100 = p[4].toFloat(),
                fiber100 = p[5].toFloat(), sugar100 = p[6].toFloat(),
                servingG = p[7].toFloat(), servingLabel = p[8],
            )
        }
    }

    /** Simple ranked search: every word must appear; names starting with the query come first. */
    fun search(query: String, limit: Int = 12): List<FoodProduct> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val words = q.split(' ').filter { it.isNotBlank() }
        return all.filter { f -> val n = f.name.lowercase(); words.all { it in n } }
            .sortedWith(compareBy({ !it.name.lowercase().startsWith(q) }, { it.name.length }))
            .take(limit)
    }
}
