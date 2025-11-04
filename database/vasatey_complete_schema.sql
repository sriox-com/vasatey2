-- =============================================================================
-- VASATEY EMERGENCY DETECTION SYSTEM - COMPLETE SUPABASE DATABASE SCHEMA
-- =============================================================================
-- This schema supports the Vasatey emergency detection application with
-- voice wake word detection, real-time alerts, and comprehensive user management
-- =============================================================================

-- Enable necessary extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "postgis" SCHEMA extensions;

-- =============================================================================
-- USERS TABLE - Core user authentication and basic info
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.users (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_sign_in_at TIMESTAMP WITH TIME ZONE,
    email_confirmed_at TIMESTAMP WITH TIME ZONE,
    phone VARCHAR(20),
    is_active BOOLEAN DEFAULT true,
    
    -- Metadata
    app_metadata JSONB DEFAULT '{}',
    user_metadata JSONB DEFAULT '{}',
    
    -- Audit fields
    created_by UUID,
    updated_by UUID
);

-- =============================================================================
-- USER PROFILES TABLE - Extended user information and preferences
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.user_profiles (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    user_id UUID REFERENCES public.users(id) ON DELETE CASCADE,
    
    -- Personal Information
    full_name VARCHAR(255),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    date_of_birth DATE,
    gender VARCHAR(20),
    avatar_url TEXT,
    
    -- Contact Information
    phone_primary VARCHAR(20),
    phone_secondary VARCHAR(20),
    address_line_1 TEXT,
    address_line_2 TEXT,
    city VARCHAR(100),
    state VARCHAR(100),
    country VARCHAR(100),
    postal_code VARCHAR(20),
    
    -- Location data for emergency services
    current_latitude DECIMAL(10, 8),
    current_longitude DECIMAL(11, 8),
    last_location_update TIMESTAMP WITH TIME ZONE,
    location_sharing_enabled BOOLEAN DEFAULT true,
    
    -- Emergency Settings
    emergency_enabled BOOLEAN DEFAULT true,
    voice_detection_enabled BOOLEAN DEFAULT true,
    wake_word_sensitivity DECIMAL(3,2) DEFAULT 0.5,
    auto_emergency_timeout INTEGER DEFAULT 30, -- seconds
    
    -- Notification Preferences
    fcm_token TEXT,
    push_notifications_enabled BOOLEAN DEFAULT true,
    sms_notifications_enabled BOOLEAN DEFAULT true,
    email_notifications_enabled BOOLEAN DEFAULT true,
    
    -- Emergency Contacts
    emergency_contact_1_name VARCHAR(255),
    emergency_contact_1_phone VARCHAR(20),
    emergency_contact_1_email VARCHAR(255),
    emergency_contact_2_name VARCHAR(255),
    emergency_contact_2_phone VARCHAR(20),
    emergency_contact_2_email VARCHAR(255),
    emergency_contact_3_name VARCHAR(255),
    emergency_contact_3_phone VARCHAR(20),
    emergency_contact_3_email VARCHAR(255),
    
    -- Medical Information (optional)
    medical_conditions TEXT,
    medications TEXT,
    allergies TEXT,
    blood_type VARCHAR(10),
    medical_notes TEXT,
    
    -- App Settings
    language_preference VARCHAR(10) DEFAULT 'en',
    timezone VARCHAR(50),
    notification_quiet_hours_start TIME,
    notification_quiet_hours_end TIME,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by UUID REFERENCES public.users(id),
    updated_by UUID REFERENCES public.users(id),
    
    UNIQUE(user_id)
);

-- =============================================================================
-- EMERGENCY ALERTS TABLE - Core emergency incident tracking
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.emergency_alerts (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    user_id UUID REFERENCES public.users(id) ON DELETE CASCADE,
    
    -- Alert Classification
    alert_type VARCHAR(50) NOT NULL DEFAULT 'general', -- 'voice_detected', 'manual', 'fall_detected', 'panic', 'medical', 'fire', 'intrusion'
    severity_level INTEGER NOT NULL DEFAULT 1, -- 1=low, 2=medium, 3=high, 4=critical, 5=emergency
    status VARCHAR(20) DEFAULT 'active', -- 'active', 'acknowledged', 'responding', 'resolved', 'false_alarm', 'cancelled'
    
    -- Trigger Information
    trigger_method VARCHAR(30) NOT NULL, -- 'voice_command', 'manual_button', 'auto_detection', 'fall_sensor', 'panic_button'
    voice_phrase_detected TEXT, -- The actual phrase that was detected
    confidence_score DECIMAL(3,2), -- 0.00 - 1.00 confidence of voice detection
    
    -- Location Data
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    altitude DECIMAL(8, 2),
    location_accuracy DECIMAL(6, 2), -- meters
    address_description TEXT,
    indoor_location TEXT, -- Room, floor, building details
    
    -- Timing
    detected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    responded_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    
    -- Response Information
    responder_type VARCHAR(30), -- 'emergency_services', 'family', 'security', 'medical', 'self_resolved'
    response_time_seconds INTEGER,
    resolution_notes TEXT,
    was_false_alarm BOOLEAN DEFAULT false,
    false_alarm_reason TEXT,
    
    -- Communication
    notifications_sent INTEGER DEFAULT 0,
    calls_made INTEGER DEFAULT 0,
    sms_sent INTEGER DEFAULT 0,
    emails_sent INTEGER DEFAULT 0,
    
    -- Device and Technical Information
    device_info JSONB, -- Phone model, OS version, app version, etc.
    battery_level INTEGER, -- Device battery percentage at time of alert
    network_type VARCHAR(20), -- 'wifi', '4g', '5g', 'ethernet'
    signal_strength INTEGER, -- Signal strength percentage
    
    -- Additional Context
    user_reported_description TEXT,
    audio_file_url TEXT, -- URL to recorded audio if applicable
    photos JSONB, -- Array of photo URLs if user added context
    weather_conditions JSONB, -- Weather at time of alert
    
    -- Verification and Validation
    requires_verification BOOLEAN DEFAULT false,
    verified_by UUID REFERENCES public.users(id),
    verified_at TIMESTAMP WITH TIME ZONE,
    verification_method VARCHAR(30), -- 'callback', 'video_call', 'on_site', 'sensor_data'
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by UUID REFERENCES public.users(id),
    updated_by UUID REFERENCES public.users(id)
);

-- =============================================================================
-- ALERT RESPONSES TABLE - Track all responses to emergency alerts
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.alert_responses (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    alert_id UUID REFERENCES public.emergency_alerts(id) ON DELETE CASCADE,
    responder_user_id UUID REFERENCES public.users(id),
    
    -- Response Details
    response_type VARCHAR(30) NOT NULL, -- 'acknowledged', 'dispatched', 'en_route', 'on_scene', 'resolved'
    response_method VARCHAR(30), -- 'app', 'phone_call', 'sms', 'email', 'radio'
    responder_name VARCHAR(255),
    responder_organization VARCHAR(255), -- 'Police', 'Fire', 'EMS', 'Family', 'Security'
    responder_contact VARCHAR(255),
    
    -- Location and Timing
    responder_latitude DECIMAL(10, 8),
    responder_longitude DECIMAL(11, 8),
    estimated_arrival_time TIMESTAMP WITH TIME ZONE,
    actual_arrival_time TIMESTAMP WITH TIME ZONE,
    
    -- Communication
    response_message TEXT,
    communication_log JSONB, -- Log of all communications
    
    -- Status Updates
    status_updates JSONB, -- Array of status updates with timestamps
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- =============================================================================
-- NOTIFICATIONS TABLE - Track all notifications sent
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.notifications (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    user_id UUID REFERENCES public.users(id) ON DELETE CASCADE,
    alert_id UUID REFERENCES public.emergency_alerts(id) ON DELETE CASCADE,
    
    -- Notification Details
    notification_type VARCHAR(30) NOT NULL, -- 'emergency_alert', 'status_update', 'system_notification', 'reminder'
    delivery_method VARCHAR(20) NOT NULL, -- 'push', 'sms', 'email', 'call', 'webhook'
    recipient_type VARCHAR(20) NOT NULL, -- 'user', 'emergency_contact', 'responder', 'system'
    
    -- Recipient Information
    recipient_identifier TEXT NOT NULL, -- phone number, email, FCM token, etc.
    recipient_name VARCHAR(255),
    
    -- Message Content
    title VARCHAR(255),
    message TEXT NOT NULL,
    priority_level INTEGER DEFAULT 1, -- 1=low, 2=normal, 3=high, 4=urgent, 5=emergency
    
    -- Delivery Status
    status VARCHAR(20) DEFAULT 'pending', -- 'pending', 'sent', 'delivered', 'read', 'failed', 'bounced'
    sent_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    read_at TIMESTAMP WITH TIME ZONE,
    failed_at TIMESTAMP WITH TIME ZONE,
    failure_reason TEXT,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    
    -- External Service Details
    external_message_id TEXT, -- ID from FCM, Twilio, etc.
    service_response JSONB, -- Raw response from external service
    cost_cents INTEGER, -- Cost in cents if applicable
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- =============================================================================
-- DEVICE SESSIONS TABLE - Track app sessions and device information
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.device_sessions (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    user_id UUID REFERENCES public.users(id) ON DELETE CASCADE,
    
    -- Device Information
    device_id VARCHAR(255) UNIQUE, -- Unique device identifier
    device_name VARCHAR(255), -- User-assigned device name
    device_type VARCHAR(50), -- 'android', 'ios', 'web'
    device_model VARCHAR(255),
    os_version VARCHAR(100),
    app_version VARCHAR(50),
    
    -- Session Details
    session_token TEXT UNIQUE,
    fcm_token TEXT,
    last_active_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    session_start_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    session_end_at TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN DEFAULT true,
    
    -- Location Tracking
    last_latitude DECIMAL(10, 8),
    last_longitude DECIMAL(11, 8),
    location_permissions_granted BOOLEAN DEFAULT false,
    microphone_permissions_granted BOOLEAN DEFAULT false,
    notification_permissions_granted BOOLEAN DEFAULT false,
    
    -- App State
    voice_detection_active BOOLEAN DEFAULT false,
    battery_optimization_disabled BOOLEAN DEFAULT false,
    background_restrictions_disabled BOOLEAN DEFAULT false,
    
    -- Performance Metrics
    cpu_usage_percent DECIMAL(5,2),
    memory_usage_mb INTEGER,
    battery_level INTEGER,
    network_type VARCHAR(20),
    signal_strength INTEGER,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- =============================================================================
-- VOICE_DETECTION_LOGS TABLE - Detailed logging of voice detection events
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.voice_detection_logs (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    user_id UUID REFERENCES public.users(id) ON DELETE CASCADE,
    session_id UUID REFERENCES public.device_sessions(id),
    alert_id UUID REFERENCES public.emergency_alerts(id), -- NULL if no alert was triggered
    
    -- Detection Details
    wake_word_detected VARCHAR(100), -- The wake word that was detected
    phrase_detected TEXT, -- The full phrase that followed the wake word
    confidence_score DECIMAL(3,2), -- 0.00 - 1.00 confidence
    processing_time_ms INTEGER, -- Time taken to process audio
    
    -- Audio Information
    audio_duration_ms INTEGER,
    audio_sample_rate INTEGER,
    audio_file_url TEXT, -- Optional: URL to stored audio sample
    noise_level DECIMAL(5,2), -- Background noise level
    
    -- Context
    trigger_source VARCHAR(30), -- 'continuous_listening', 'manual_activation', 'keyword_spotted'
    was_false_positive BOOLEAN DEFAULT false,
    user_confirmed BOOLEAN, -- Did user confirm this was intentional?
    
    -- Device State
    battery_level INTEGER,
    is_charging BOOLEAN,
    screen_on BOOLEAN,
    app_in_foreground BOOLEAN,
    
    -- Location (if available)
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    
    -- Audit fields
    detected_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- =============================================================================
-- SYSTEM_LOGS TABLE - Application events and system monitoring
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.system_logs (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    user_id UUID REFERENCES public.users(id),
    session_id UUID REFERENCES public.device_sessions(id),
    alert_id UUID REFERENCES public.emergency_alerts(id),
    
    -- Log Classification
    log_level VARCHAR(10) NOT NULL, -- 'DEBUG', 'INFO', 'WARN', 'ERROR', 'CRITICAL'
    category VARCHAR(50) NOT NULL, -- 'AUTH', 'VOICE', 'LOCATION', 'NOTIFICATION', 'DATABASE', 'NETWORK'
    event_type VARCHAR(100) NOT NULL, -- Specific event like 'login_success', 'voice_detection_started'
    
    -- Log Content
    message TEXT NOT NULL,
    error_code VARCHAR(50),
    stack_trace TEXT,
    
    -- Context Data
    context_data JSONB, -- Additional structured data
    user_agent TEXT,
    ip_address INET,
    request_id VARCHAR(100),
    
    -- Performance Metrics
    execution_time_ms INTEGER,
    memory_usage_mb INTEGER,
    cpu_usage_percent DECIMAL(5,2),
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- =============================================================================
-- APP_SETTINGS TABLE - Global application configuration
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.app_settings (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    setting_key VARCHAR(255) UNIQUE NOT NULL,
    setting_value JSONB NOT NULL,
    setting_type VARCHAR(50) NOT NULL, -- 'string', 'number', 'boolean', 'object', 'array'
    description TEXT,
    is_public BOOLEAN DEFAULT false, -- Can be read by app users
    is_user_configurable BOOLEAN DEFAULT false, -- Can be modified by users
    
    -- Validation
    validation_rules JSONB, -- JSON schema for validating the value
    default_value JSONB,
    
    -- Environment and Deployment
    environment VARCHAR(20) DEFAULT 'production', -- 'development', 'staging', 'production'
    version VARCHAR(20) DEFAULT '1.0.0',
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by UUID REFERENCES public.users(id),
    updated_by UUID REFERENCES public.users(id)
);

-- =============================================================================
-- INDEXES for Performance Optimization
-- =============================================================================

-- Users table indexes
CREATE INDEX IF NOT EXISTS idx_users_email ON public.users(email);
CREATE INDEX IF NOT EXISTS idx_users_created_at ON public.users(created_at);
CREATE INDEX IF NOT EXISTS idx_users_is_active ON public.users(is_active);

-- User Profiles table indexes
CREATE INDEX IF NOT EXISTS idx_user_profiles_user_id ON public.user_profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_profiles_fcm_token ON public.user_profiles(fcm_token);
CREATE INDEX IF NOT EXISTS idx_user_profiles_location ON public.user_profiles(current_latitude, current_longitude);
CREATE INDEX IF NOT EXISTS idx_user_profiles_emergency_enabled ON public.user_profiles(emergency_enabled);

-- Emergency Alerts table indexes
CREATE INDEX IF NOT EXISTS idx_emergency_alerts_user_id ON public.emergency_alerts(user_id);
CREATE INDEX IF NOT EXISTS idx_emergency_alerts_status ON public.emergency_alerts(status);
CREATE INDEX IF NOT EXISTS idx_emergency_alerts_detected_at ON public.emergency_alerts(detected_at DESC);
CREATE INDEX IF NOT EXISTS idx_emergency_alerts_severity ON public.emergency_alerts(severity_level);
CREATE INDEX IF NOT EXISTS idx_emergency_alerts_location ON public.emergency_alerts(latitude, longitude);
CREATE INDEX IF NOT EXISTS idx_emergency_alerts_type_status ON public.emergency_alerts(alert_type, status);

-- Alert Responses table indexes
CREATE INDEX IF NOT EXISTS idx_alert_responses_alert_id ON public.alert_responses(alert_id);
CREATE INDEX IF NOT EXISTS idx_alert_responses_responder ON public.alert_responses(responder_user_id);
CREATE INDEX IF NOT EXISTS idx_alert_responses_created_at ON public.alert_responses(created_at DESC);

-- Notifications table indexes
CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON public.notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_alert_id ON public.notifications(alert_id);
CREATE INDEX IF NOT EXISTS idx_notifications_status ON public.notifications(status);
CREATE INDEX IF NOT EXISTS idx_notifications_delivery_method ON public.notifications(delivery_method);
CREATE INDEX IF NOT EXISTS idx_notifications_created_at ON public.notifications(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_retry ON public.notifications(status, next_retry_at) WHERE status = 'failed' AND next_retry_at IS NOT NULL;

-- Device Sessions table indexes
CREATE INDEX IF NOT EXISTS idx_device_sessions_user_id ON public.device_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_device_sessions_device_id ON public.device_sessions(device_id);
CREATE INDEX IF NOT EXISTS idx_device_sessions_active ON public.device_sessions(is_active, last_active_at);
CREATE INDEX IF NOT EXISTS idx_device_sessions_fcm_token ON public.device_sessions(fcm_token);

-- Voice Detection Logs table indexes
CREATE INDEX IF NOT EXISTS idx_voice_detection_logs_user_id ON public.voice_detection_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_voice_detection_logs_session_id ON public.voice_detection_logs(session_id);
CREATE INDEX IF NOT EXISTS idx_voice_detection_logs_detected_at ON public.voice_detection_logs(detected_at DESC);
CREATE INDEX IF NOT EXISTS idx_voice_detection_logs_confidence ON public.voice_detection_logs(confidence_score);
CREATE INDEX IF NOT EXISTS idx_voice_detection_logs_false_positive ON public.voice_detection_logs(was_false_positive);

-- System Logs table indexes
CREATE INDEX IF NOT EXISTS idx_system_logs_user_id ON public.system_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_system_logs_level_category ON public.system_logs(log_level, category);
CREATE INDEX IF NOT EXISTS idx_system_logs_created_at ON public.system_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_system_logs_error_level ON public.system_logs(log_level) WHERE log_level IN ('ERROR', 'CRITICAL');

-- App Settings table indexes
CREATE INDEX IF NOT EXISTS idx_app_settings_key ON public.app_settings(setting_key);
CREATE INDEX IF NOT EXISTS idx_app_settings_public ON public.app_settings(is_public) WHERE is_public = true;

-- =============================================================================
-- ROW LEVEL SECURITY POLICIES
-- =============================================================================

-- Enable RLS on all tables
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.emergency_alerts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.alert_responses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.device_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.voice_detection_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.system_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_settings ENABLE ROW LEVEL SECURITY;

-- Users can only see and modify their own data
CREATE POLICY "Users can view own profile" ON public.users
    FOR SELECT USING (auth.uid() = id);

CREATE POLICY "Users can update own profile" ON public.users
    FOR UPDATE USING (auth.uid() = id);

-- User profiles policies
CREATE POLICY "Users can view own user profile" ON public.user_profiles
    FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "Users can update own user profile" ON public.user_profiles
    FOR UPDATE USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own user profile" ON public.user_profiles
    FOR INSERT WITH CHECK (auth.uid() = user_id);

-- Emergency alerts policies
CREATE POLICY "Users can view own emergency alerts" ON public.emergency_alerts
    FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own emergency alerts" ON public.emergency_alerts
    FOR INSERT WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own emergency alerts" ON public.emergency_alerts
    FOR UPDATE USING (auth.uid() = user_id);

-- Alert responses - emergency contacts and responders can view
CREATE POLICY "Users can view responses to their alerts" ON public.alert_responses
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM public.emergency_alerts 
            WHERE id = alert_responses.alert_id 
            AND user_id = auth.uid()
        )
    );

-- Notifications policies
CREATE POLICY "Users can view own notifications" ON public.notifications
    FOR SELECT USING (auth.uid() = user_id);

-- Device sessions policies
CREATE POLICY "Users can view own device sessions" ON public.device_sessions
    FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "Users can update own device sessions" ON public.device_sessions
    FOR UPDATE USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own device sessions" ON public.device_sessions
    FOR INSERT WITH CHECK (auth.uid() = user_id);

-- Voice detection logs policies
CREATE POLICY "Users can view own voice logs" ON public.voice_detection_logs
    FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own voice logs" ON public.voice_detection_logs
    FOR INSERT WITH CHECK (auth.uid() = user_id);

-- System logs policies (users can only view their own logs)
CREATE POLICY "Users can view own system logs" ON public.system_logs
    FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "System can insert all logs" ON public.system_logs
    FOR INSERT WITH CHECK (true);

-- App settings policies (public settings are readable by all authenticated users)
CREATE POLICY "Authenticated users can view public app settings" ON public.app_settings
    FOR SELECT USING (auth.role() = 'authenticated' AND is_public = true);

-- =============================================================================
-- TRIGGERS for automatic timestamp updates
-- =============================================================================

-- Function to update timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers for updated_at columns
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON public.users 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_profiles_updated_at BEFORE UPDATE ON public.user_profiles 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_emergency_alerts_updated_at BEFORE UPDATE ON public.emergency_alerts 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_alert_responses_updated_at BEFORE UPDATE ON public.alert_responses 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_notifications_updated_at BEFORE UPDATE ON public.notifications 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_device_sessions_updated_at BEFORE UPDATE ON public.device_sessions 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_app_settings_updated_at BEFORE UPDATE ON public.app_settings 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =============================================================================
-- INITIAL DATA - Default app settings and configuration
-- =============================================================================

INSERT INTO public.app_settings (setting_key, setting_value, setting_type, description, is_public, is_user_configurable) VALUES
('app_version', '"1.0.0"', 'string', 'Current application version', true, false),
('emergency_timeout_seconds', '30', 'number', 'Default timeout for emergency detection before auto-trigger', true, false),
('max_voice_detection_attempts', '3', 'number', 'Maximum voice detection attempts before disabling', true, false),
('voice_confidence_threshold', '0.75', 'number', 'Minimum confidence score for voice detection', true, false),
('notification_retry_attempts', '3', 'number', 'Maximum notification retry attempts', false, false),
('location_update_interval_ms', '30000', 'number', 'Interval for location updates in milliseconds', true, false),
('emergency_contact_limit', '3', 'number', 'Maximum number of emergency contacts per user', true, false),
('supported_languages', '["en", "es", "fr", "de", "it", "pt", "zh", "ja", "ko", "ar", "hi", "ru"]', 'array', 'Supported language codes', true, false),
('maintenance_mode', 'false', 'boolean', 'Enable maintenance mode', true, false),
('debug_mode', 'false', 'boolean', 'Enable debug logging', false, false)
ON CONFLICT (setting_key) DO NOTHING;

-- =============================================================================
-- FUNCTIONS for common operations
-- =============================================================================

-- Function to get user's active emergency alerts
CREATE OR REPLACE FUNCTION get_active_emergency_alerts(user_uuid UUID)
RETURNS TABLE (
    alert_id UUID,
    alert_type VARCHAR(50),
    severity_level INTEGER,
    detected_at TIMESTAMP WITH TIME ZONE,
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8)
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        ea.id,
        ea.alert_type,
        ea.severity_level,
        ea.detected_at,
        ea.latitude,
        ea.longitude
    FROM public.emergency_alerts ea
    WHERE ea.user_id = user_uuid 
    AND ea.status IN ('active', 'acknowledged', 'responding')
    ORDER BY ea.detected_at DESC;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Function to create new emergency alert with automatic notification
CREATE OR REPLACE FUNCTION create_emergency_alert(
    p_user_id UUID,
    p_alert_type VARCHAR(50),
    p_severity_level INTEGER,
    p_trigger_method VARCHAR(30),
    p_latitude DECIMAL(10, 8) DEFAULT NULL,
    p_longitude DECIMAL(11, 8) DEFAULT NULL,
    p_voice_phrase TEXT DEFAULT NULL,
    p_confidence_score DECIMAL(3,2) DEFAULT NULL
)
RETURNS UUID AS $$
DECLARE
    alert_id UUID;
BEGIN
    INSERT INTO public.emergency_alerts (
        user_id, alert_type, severity_level, trigger_method, 
        latitude, longitude, voice_phrase_detected, confidence_score
    ) VALUES (
        p_user_id, p_alert_type, p_severity_level, p_trigger_method,
        p_latitude, p_longitude, p_voice_phrase, p_confidence_score
    ) RETURNING id INTO alert_id;
    
    RETURN alert_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Function to update user's last location
CREATE OR REPLACE FUNCTION update_user_location(
    p_user_id UUID,
    p_latitude DECIMAL(10, 8),
    p_longitude DECIMAL(11, 8)
)
RETURNS BOOLEAN AS $$
BEGIN
    UPDATE public.user_profiles 
    SET 
        current_latitude = p_latitude,
        current_longitude = p_longitude,
        last_location_update = NOW()
    WHERE user_id = p_user_id;
    
    RETURN FOUND;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- =============================================================================
-- VIEWS for common data access patterns
-- =============================================================================

-- View for user dashboard data
CREATE OR REPLACE VIEW user_dashboard AS
SELECT 
    u.id as user_id,
    u.email,
    up.full_name,
    up.emergency_enabled,
    up.voice_detection_enabled,
    up.fcm_token,
    up.current_latitude,
    up.current_longitude,
    up.last_location_update,
    (
        SELECT COUNT(*) 
        FROM public.emergency_alerts ea 
        WHERE ea.user_id = u.id 
        AND ea.status IN ('active', 'acknowledged', 'responding')
    ) as active_alerts_count,
    (
        SELECT MAX(ds.last_active_at)
        FROM public.device_sessions ds 
        WHERE ds.user_id = u.id 
        AND ds.is_active = true
    ) as last_active_session
FROM public.users u
LEFT JOIN public.user_profiles up ON u.id = up.user_id;

-- View for alert summary with response information
CREATE OR REPLACE VIEW alert_summary AS
SELECT 
    ea.id as alert_id,
    ea.user_id,
    ea.alert_type,
    ea.severity_level,
    ea.status,
    ea.detected_at,
    ea.latitude,
    ea.longitude,
    ea.voice_phrase_detected,
    ea.confidence_score,
    up.full_name as user_name,
    up.phone_primary as user_phone,
    (
        SELECT COUNT(*) 
        FROM public.alert_responses ar 
        WHERE ar.alert_id = ea.id
    ) as response_count,
    (
        SELECT COUNT(*) 
        FROM public.notifications n 
        WHERE n.alert_id = ea.id 
        AND n.status = 'delivered'
    ) as notifications_delivered
FROM public.emergency_alerts ea
LEFT JOIN public.user_profiles up ON ea.user_id = up.user_id;

-- =============================================================================
-- SCHEMA VALIDATION AND HEALTH CHECK
-- =============================================================================

-- Function to validate schema health
CREATE OR REPLACE FUNCTION validate_schema_health()
RETURNS TABLE (
    table_name TEXT,
    row_count BIGINT,
    last_modified TIMESTAMP WITH TIME ZONE
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        'users'::TEXT,
        (SELECT COUNT(*) FROM public.users),
        (SELECT MAX(updated_at) FROM public.users)
    UNION ALL
    SELECT 
        'user_profiles'::TEXT,
        (SELECT COUNT(*) FROM public.user_profiles),
        (SELECT MAX(updated_at) FROM public.user_profiles)
    UNION ALL
    SELECT 
        'emergency_alerts'::TEXT,
        (SELECT COUNT(*) FROM public.emergency_alerts),
        (SELECT MAX(updated_at) FROM public.emergency_alerts)
    UNION ALL
    SELECT 
        'notifications'::TEXT,
        (SELECT COUNT(*) FROM public.notifications),
        (SELECT MAX(updated_at) FROM public.notifications);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- =============================================================================
-- COMMENTS for documentation
-- =============================================================================

COMMENT ON TABLE public.users IS 'Core user authentication and basic information';
COMMENT ON TABLE public.user_profiles IS 'Extended user profiles with emergency settings and preferences';
COMMENT ON TABLE public.emergency_alerts IS 'Emergency incidents and alert tracking';
COMMENT ON TABLE public.alert_responses IS 'Responses and actions taken for emergency alerts';
COMMENT ON TABLE public.notifications IS 'All notifications sent via various channels';
COMMENT ON TABLE public.device_sessions IS 'Active device sessions and technical information';
COMMENT ON TABLE public.voice_detection_logs IS 'Detailed logs of voice detection events';
COMMENT ON TABLE public.system_logs IS 'Application events and system monitoring';
COMMENT ON TABLE public.app_settings IS 'Global application configuration settings';

-- =============================================================================
-- END OF SCHEMA
-- =============================================================================