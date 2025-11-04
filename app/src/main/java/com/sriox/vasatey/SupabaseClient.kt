package com.sriox.vasatey

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defa naultRequest
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object SupabaseClient {
    
    // Updated Supabase project credentials - matching supabase-config.json
    private const val SUPABASE_URL = "https://hbxxfclyuhzdstmikzkt.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhieHhmY2x5dWh6ZHN0bWlremt0Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjIwNjExNDMsImV4cCI6MjA3NzYzNzE0M30.AdTMGXdqQJP3vy34JOS_mtPUXXPzGMJgUOx8DWlSZ3g"
    
    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Auth) {
            // Android-specific auth configuration
            autoRefreshToken = true
            persistSession = true
        }
        install(Postgrest)
        install(Storage)
        
        // Configure HTTP client for Android
        httpConfig {
            engine {
                Android
            }
            
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            
            install(HttpRequestRetry) {
                maxRetries = 3
                retryDelay { 1000 }
            }
            
            defaultRequest {
                header("User-Agent", "Vasatey-Android/1.0")
                header("X-Client-Info", "supabase-kotlin/android")
            }
        }
    }
}