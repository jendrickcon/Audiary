package com.example.audiary.data

import com.example.audiary.model.Song

private fun song(
    id: String, title: String, artist: String, album: String,
    length: String, year: String, color: Long
): Song {
    val (m, s) = length.split(":").map { it.toLong() }
    return Song("demo:$id", title, artist, album, (m * 60 + s) * 1000, year, color,
        albumId = "demo:$artist:$album")
}

class FakeMusicRepository : MusicRepository {
    private val songs = listOf(
        song("nights", "Nights", "Frank Ocean", "Blonde", "5:07", "2016", 0xFFE0A526),
        song("ivy", "Ivy", "Frank Ocean", "Blonde", "4:09", "2016", 0xFF3E7C59),
        song("505", "505", "Arctic Monkeys", "Favourite Worst Nightmare", "4:13", "2007", 0xFF8C2F39),
        song("dowantto", "Do I Wanna Know?", "Arctic Monkeys", "AM", "4:32", "2013", 0xFF4A4E69),
        song("lessiknow", "The Less I Know the Better", "Tame Impala", "Currents", "3:36", "2015", 0xFFD1495B),
        song("sweetdisp", "Sweet Disposition", "The Temper Trap", "Conditions", "3:52", "2009", 0xFF2A9D8F),
        song("apocalypse", "Apocalypse", "Cigarettes After Sex", "Cigarettes After Sex", "4:51", "2017", 0xFF5C6B73),
        song("blinding", "Blinding Lights", "The Weeknd", "After Hours", "3:20", "2020", 0xFFC1121F),
        song("redbone", "Redbone", "Childish Gambino", "Awaken, My Love!", "5:27", "2016", 0xFFB5651D),
        song("spacesong", "Space Song", "Beach House", "Depression Cherry", "5:20", "2015", 0xFF7B2D43),
        song("electric", "Electric Feel", "MGMT", "Oracular Spectacular", "3:49", "2007", 0xFF6A4C93),
        song("brightside", "Mr. Brightside", "The Killers", "Hot Fuss", "3:43", "2004", 0xFFE76F51),
        song("heatwaves", "Heat Waves", "Glass Animals", "Dreamland", "3:58", "2020", 0xFFF4A261),
        song("instant", "Instant Crush", "Daft Punk", "Random Access Memories", "5:37", "2013", 0xFF9C6644),
        song("dreams", "Dreams", "Fleetwood Mac", "Rumours", "4:14", "1977", 0xFF8D6B94),
        song("bags", "Bags", "Clairo", "Immunity", "4:21", "2019", 0xFFE5989B),
        song("motion", "Motion Sickness", "Phoebe Bridgers", "Stranger in the Alps", "3:44", "2017", 0xFF457B9D),
        song("pinkmoon", "Pink Moon", "Nick Drake", "Pink Moon", "2:03", "1972", 0xFFD98BA3),
        song("yesterday", "Lost in Yesterday", "Tame Impala", "The Slow Rush", "4:09", "2020", 0xFF2B6F8E),
        song("skinny", "Skinny Love", "Bon Iver", "For Emma, Forever Ago", "3:58", "2007", 0xFF6B705C),
        song("midnight", "Midnight City", "M83", "Hurry Up, We're Dreaming", "4:03", "2011", 0xFF1D3A8A),
        song("genesis", "Genesis", "Grimes", "Visions", "4:16", "2012", 0xFFB5179E),
        song("slowdance", "Slow Dancing in a Burning Room", "John Mayer", "Continuum", "4:02", "2006", 0xFF9A3B26),
        song("holocene", "Holocene", "Bon Iver", "Bon Iver", "5:36", "2011", 0xFF4F7CAC),
        song("alright", "Alright", "Kendrick Lamar", "To Pimp a Butterfly", "3:39", "2015", 0xFF588157)
    )

    override suspend fun getSongs() = songs
    override suspend fun getSong(id: String) = songs.firstOrNull { it.id == id }
}
