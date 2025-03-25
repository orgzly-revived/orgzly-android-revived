package com.orgzly.android.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orgzly.android.OrgzlyTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

class OrgzlyDatabaseTest : OrgzlyTest() {

    @Test
    fun testDefaultSearchesPopulation() {
        val searches = dataRepository.getSavedSearches()
        
        assertEquals(4, searches.size)
        
        assertEquals("Agenda", searches[0].name)
        assertEquals(".it.done ad.7 o.s", searches[0].query)
        assertEquals(1, searches[0].position)
        
        assertEquals("Next 3 days", searches[1].name)
        assertEquals(".it.done s.ge.today ad.3 o.s", searches[1].query)
        assertEquals(2, searches[1].position)
        
        assertEquals("Scheduled", searches[2].name)
        assertEquals("s.today .it.done o.s", searches[2].query)
        assertEquals(3, searches[2].position)
        
        assertEquals("To Do", searches[3].name)
        assertEquals("i.todo o.s", searches[3].query)
        assertEquals(4, searches[3].position)
    }
} 