package com.miku.ray

object AppConfig {

    const val ANG_PACKAGE = BuildConfig.APPLICATION_ID
    const val TAG = BuildConfig.APPLICATION_ID

    const val DIR_ASSETS = "assets"

    // Stored in ServerAffiliationInfo.countryCode when a country-code probe was attempted but
    // failed, so the UI can show a failure indicator instead of treating it the same as "never
    // tested" (null). Deliberately not a valid 2-letter ISO code so it never collides with a
    // real result.
    const val COUNTRY_CODE_TEST_FAILED = "FAILED"

    const val WEBDAV_BACKUP_DIR = "backups"
    const val WEBDAV_BACKUP_FILE_NAME = "backup_ng.mikubackup"

    const val ANG_CONFIG = "ang_config"

    const val DEFAULT_SUBSCRIPTION_ID = "__default_subscription__"

    const val PREF_SNIFFING_ENABLED = "pref_sniffing_enabled"
    const val PREF_ROUTE_ONLY_ENABLED = "pref_route_only_enabled"
    const val PREF_PER_APP_PROXY = "pref_per_app_proxy"
    const val PREF_PER_APP_PROXY_SET = "pref_per_app_proxy_set"
    const val PREF_BYPASS_APPS = "pref_bypass_apps"
    const val PREF_LOCAL_DNS_ENABLED = "pref_local_dns_enabled"
    const val PREF_FAKE_DNS_ENABLED = "pref_fake_dns_enabled"
    const val PREF_FAKE_DNS_IP_POOL = "pref_fake_dns_ip_pool"
    const val PREF_APPEND_HTTP_PROXY = "pref_append_http_proxy"
    const val PREF_LOCAL_DNS_PORT = "pref_local_dns_port"
    const val PREF_VPN_DNS = "pref_vpn_dns"
    const val PREF_VPN_BYPASS_LAN = "pref_vpn_bypass_lan"
    const val PREF_VPN_INTERFACE_ADDRESS_CONFIG_INDEX = "pref_vpn_interface_address_config_index"
    const val PREF_VPN_MTU = "pref_vpn_mtu"
    const val PREF_ROUTING_DOMAIN_STRATEGY = "pref_routing_domain_strategy"
    const val PREF_ROUTING_RULESET = "pref_routing_ruleset"
    const val PREF_TCP_KEEPALIVE_IDLE = "pref_tcp_keepalive_idle"
    const val PREF_WS_HEARTBEAT_PERIOD = "pref_ws_heartbeat_period"
    const val PREF_FRAGMENT_ENABLED = "pref_fragment_enabled"
    const val PREF_FRAGMENT_PACKETS = "pref_fragment_packets"
    const val PREF_FRAGMENT_LENGTH = "pref_fragment_length"
    const val PREF_FRAGMENT_INTERVAL = "pref_fragment_interval"
    const val PREF_FRAGMENT_MAXSPLIT = "pref_fragment_maxsplit"
    const val SUBSCRIPTION_UPDATE_TASK_NAME = "subscription_updater"
    const val SUBSCRIPTION_MIN_INTERVAL_MINUTES = 15L
    const val PREF_SPEED_ENABLED = "pref_speed_enabled"
    const val PREF_HIDE_DIRECT_TRAFFIC_NOTIFICATION = "pref_hide_direct_traffic_notification"
    const val PREF_SHOW_GROUP_NAME_NOTIFICATION = "pref_show_group_name_notification"
    const val PREF_TRAFFIC_ENABLED = "pref_traffic_enabled"
    const val PREF_NETWORK_SECURITY_ENABLED = "pref_network_security_enabled"
    const val PREF_HIDE_SCROLL_BUTTONS = "pref_hide_scroll_buttons"
    const val PREF_COMPACT_LIST_ACTIONS = "pref_compact_list_actions"
    const val PREF_FAB_EXTENDED = "pref_fab_extended"
    const val PREF_VPN_CONNECT_START_TIME = "pref_vpn_connect_start_time"

    /**
     * Whether a tunnel is supposed to be up. Persisted so the guard (see
     * TunnelGuard) can tell "the user wants to be connected" apart from
     * "nothing was ever asked for" after the process was killed.
     */
    const val PREF_TUNNEL_EXPECTED = "pref_tunnel_expected"
    const val PREF_SEND_HWID = "pref_send_hwid"
    const val PREF_HIDE_FROM_RECENT_APPS = "hide_from_recent_apps"
    const val PREF_CONFIRM_REMOVE = "pref_confirm_remove"
    const val PREF_START_SCAN_IMMEDIATE = "pref_start_scan_immediate"
    const val PREF_DOUBLE_COLUMN_DISPLAY = "pref_double_column_display"
    const val PREF_IPV6_ENABLED = "pref_ipv6_enabled"
    const val PREF_PREFER_IPV6 = "pref_prefer_ipv6"
    const val PREF_PROXY_SHARING = "pref_proxy_sharing_enabled"
    const val PREF_ENABLE_LOCAL_PROXY = "pref_enable_local_proxy"
    const val PREF_SOCKS_PORT = "pref_socks_port"
    const val PREF_DYNAMIC_SOCKS_PORT = "pref_dynamic_socks_port"
    const val PREF_SOCKS_USERNAME = "pref_socks_username"
    const val PREF_SOCKS_PASSWORD = "pref_socks_password"
    const val PREF_SOCKS_ENABLE_UDP = "pref_socks_enable_udp"
    const val PREF_REMOTE_DNS = "pref_remote_dns"
    const val PREF_DOMESTIC_DNS = "pref_domestic_dns"
    const val PREF_DNS_HOSTS = "pref_dns_hosts"
    const val PREF_DELAY_TEST_URL = "pref_delay_test_url"
    const val PREF_IP_API_URL = "pref_ip_api_url"
    const val PREF_SHOW_ISP_INFO = "pref_show_isp_info"
    const val PREF_LOGLEVEL = "pref_core_loglevel"
    const val PREF_OUTBOUND_DOMAIN_RESOLVE_METHOD = "pref_outbound_domain_resolve_method"
    const val PREF_MODE = "pref_mode"
    const val PREF_ROOT_MODE_ENABLE = "pref_root_mode_enabled"
    const val PREF_ROOT_LAN_SHARING = "pref_root_lan_sharing"
    const val PREF_IS_BOOTED = "pref_is_booted"
    const val PREF_SYSTEM_VPN_SETTINGS = "pref_system_vpn_settings"
    const val PREF_CHECK_UPDATE_PRE_RELEASE = "pref_check_update_pre_release"
    const val PREF_GEO_FILES_SOURCES = "pref_geo_files_sources"
    const val PREF_USE_HEV_TUNNEL = "pref_use_hev_tunnel_v2"
    const val PREF_HEV_TUNNEL_LOGLEVEL = "pref_hev_tunnel_loglevel"
    const val PREF_HEV_TUNNEL_RW_TIMEOUT = "pref_hev_tunnel_rw_timeout_v2"
    const val PREF_HEV_TUNNEL_ICMP = "pref_hev_tunnel_icmp"
    const val PREF_HEV_TUNNEL_UDP_MODE = "pref_hev_tunnel_udp_mode"
    const val PREF_HEV_TUNNEL_UDP_ADDRESS = "pref_hev_tunnel_udp_address"
    const val PREF_HEV_TUNNEL_TCP_FASTOPEN = "pref_hev_tunnel_tcp_fastopen"
    const val PREF_HEV_TUNNEL_PIPELINE = "pref_hev_tunnel_pipeline"
    const val PREF_UPDATE_SUBSCRIPTION = "pref_update_subscription"
    const val PREF_AUTO_TEST_AFTER_UPDATE_SUBSCRIPTION = "pref_auto_test_after_update_subscription"
    const val PREF_AUTO_REMOVE_INVALID_AFTER_TEST = "pref_auto_remove_invalid_after_test"
    const val PREF_REAL_PING_CONCURRENCY = "pref_real_ping_concurrency"
    const val PREF_COUNTRY_CODE_TIMEOUT = "pref_country_code_timeout"

    const val DEFAULT_SOCKS_ENABLE_UDP = true
    const val DEFAULT_OUTBOUND_DOMAIN_RESOLVE_METHOD = "1"
    const val DEFAULT_VPN_BYPASS_LAN = "1"
    const val DEFAULT_HEV_TUNNEL_LOGLEVEL = "warn"
    const val DEFAULT_MUX_XUDP_CONCURRENCY = "8"

    const val PREF_NAVIGATE_UI_SETTINGS = "pref_navigate_ui_settings"
    const val PREF_NAVIGATE_UI_HOME = "pref_navigate_ui_home"
    const val PREF_NAVIGATE_UI_BANNER = "pref_navigate_ui_banner"
    const val PREF_NAVIGATE_UI_ALERTS = "pref_navigate_ui_alerts"
    const val PREF_NAVIGATE_ABOUT_UPDATE = "pref_navigate_about_update"
    const val PREF_NAVIGATE_CHECK_UPDATE = "pref_navigate_check_update"
    const val PREF_AUTO_CHECK_UPDATE = "pref_auto_check_update"
    const val PREF_NAVIGATE_VPN_SETTINGS = "pref_navigate_vpn_settings"
    const val PREF_NAVIGATE_CORE_SETTINGS = "pref_navigate_core_settings"
    const val PREF_NAVIGATE_FRAGMENT_SETTINGS = "pref_navigate_fragment_settings"
    const val PREF_NAVIGATE_ADVANCED_SETTINGS = "pref_navigate_advanced_settings"
    const val PREF_NAVIGATE_PER_APP_PROXY_SETTINGS = "pref_navigate_per_app_proxy_settings"
    const val PREF_SERVER_ORDER = "pref_server_order"
    const val PREF_SUB_SORT_ORDER = "pref_sub_sort_order"

    const val PREF_APP_THEME = "pref_app_theme"
    const val PREF_DYNAMIC_COLOR = "pref_dynamic_color"
    const val PREF_DYNAMIC_COLOR_BANNER = "pref_dynamic_color_banner"
    const val PREF_BANNER_COLOR = "pref_banner_color"
    const val PREF_TRUE_BLACK = "pref_true_black"
    const val PREF_CATEGORY_STYLE = "pref_category_style"
    const val PREF_APP_FONT = "pref_app_font"
    const val PREF_APP_FONT_USE_CUSTOM = "pref_app_font_use_custom"
    const val PREF_APP_FONT_CUSTOM_NAME = "pref_app_font_custom_name"
    const val PREF_CUSTOM_APP_NAME = "pref_custom_app_name"
    const val PREF_ACTION_PICK_CUSTOM_FONT = "action_pick_custom_font"
    const val PREF_ACTION_DELETE_CUSTOM_FONT = "action_delete_custom_font"
    const val PREF_CUSTOM_COLOR = "pref_custom_color"
    const val PREF_USE_CUSTOM_COLOR = "pref_use_custom_color"
    const val PREF_ENABLE_BLUR = "pref_enable_blur"
    const val PREF_USE_SYSTEM_BLUR = "pref_use_system_blur"
    const val PREF_BLUR_BOTTOM_STATUS = "pref_blur_bottom_status"
    const val PREF_BLUR_INTENSITY = "pref_blur_intensity"
    const val PREF_BLUR_RADIUS = "pref_blur_radius"
    const val PREF_BLUR_ROUNDS = "pref_blur_rounds"
    const val DEFAULT_BLUR_RADIUS = 10
    const val DEFAULT_BLUR_ROUNDS = 3
    const val PREF_BLUR_BOTTOM_INTENSITY = "pref_blur_bottom_intensity"
    const val PREF_BLUR_BOTTOM_RADIUS = "pref_blur_bottom_radius"
    const val DEFAULT_BLUR_BOTTOM_RADIUS = 2.5f
    const val PREF_BLUR_BOTTOM_ALPHA = "pref_blur_bottom_alpha"
    // N14: 25% glass over an arbitrary wallpaper could not guarantee any
    // minimum contrast for the status text. 60% keeps the glass look while
    // giving the foreground a fighting chance on every backdrop; users who
    // saved a preference keep theirs.
    const val DEFAULT_BLUR_BOTTOM_ALPHA = 60
    const val PREF_BLUR_BOTTOM_BLOB_ANIM = "pref_blur_bottom_blob_anim"
    const val PREF_ICON_SHAPE = "pref_icon_shape"
    const val PREF_ICON_SHAPE_DEFAULT = "uwu_shape_cookie_9"
    const val PREF_ARROW_SHAPE = "pref_arrow_shape"
    const val PREF_ARROW_SHAPE_DEFAULT = "uwu_shape_circle"

    const val PREF_APP_ICON = "pref_app_icon"
    const val APP_ICON_DEFAULT = "default"
    const val APP_ICON_MIKU_TEAL = "mikuTeal"
    const val APP_ICON_BASIC = "basic"
    const val APP_ICON_CHERRY_POP = "cherryPop"
    const val APP_ICON_RABBIT_HOLE = "rabbitHole"
    const val APP_ICON_MESMERIZER = "mesmerizer"
    const val APP_ICON_SAKURA = "sakura"
    const val APP_ICON_MAGICAL_MIRAI_2024 = "magicalMirai2024"
    const val APP_ICON_DEEP_SEA_GIRL = "deepSeaGirl"
    const val APP_ICON_SNOW_MIKU_2025 = "snowMiku2025"
    const val APP_ICON_SYMPHONY_2022 = "symphony2022"
    const val APP_ICON_RACING_MIKU_2025 = "racingMiku2025"
    const val APP_ICON_CINNAMIKU = "cinnamiku"
    const val APP_ICON_RETRY_NOW = "retryNow"

    const val APP_NAME_DEFAULT = "default"
    const val APP_NAME_MIKU1 = "miku1"
    const val APP_NAME_MIKU2 = "miku2"
    const val APP_NAME_MIKU3 = "miku3"
    const val APP_NAME_MIKU4 = "miku4"
    const val APP_NAME_MIKU5 = "miku5"
    const val APP_NAME_MIKU6 = "miku6"
    const val APP_NAME_MIKU7 = "miku7"
    const val APP_NAME_MIKU8 = "miku8"
    const val APP_NAME_MIKU9 = "miku9"
    const val APP_NAME_MIKU10 = "miku10"
    const val APP_NAME_MIKU11 = "miku11"
    const val APP_NAME_MIKU12 = "miku12"
    const val PREF_INDICATOR_STYLE = "pref_indicator_style"
    const val PREF_GROUP_ALL_DISPLAY = "pref_group_all_display"
    const val PREF_GROUP_ALL_TAB_ICON = "pref_group_all_tab_icon"
    const val PREF_TAB_BADGE_LIMIT = "pref_tab_badge_limit"
    const val PREF_LANGUAGE = "pref_language"
    const val PREF_UI_MODE_NIGHT = "pref_ui_mode_night"
    const val PREF_CUSTOM_DPI = "pref_custom_dpi"
    const val PREF_APP_FONT_SIZE = "pref_app_font_size"
    const val PREF_SOUND_ON_CONNECT = "pref_sound_on_connect"
    const val PREF_CUSTOM_CONNECT_SOUND_URI = "pref_custom_connect_sound_uri"
    const val PREF_CUSTOM_DISCONNECT_SOUND_URI = "pref_custom_disconnect_sound_uri"
    const val PREF_SHOW_SPLASH = "pref_show_splash_screen"
    const val PREF_KEEP_AWAKE = "pref_keep_awake"
    const val PREF_SEARCH_BAR_CHIP = "pref_search_bar_chip"
    const val PREF_SEARCH_BAR_CHIP_DUAL_SELECTION = "pref_search_bar_chip_dual_selection"
    const val PREF_WEATHER_CACHE_ENTRY = "pref_weather_cache_entry"
    const val PREF_WEATHER_USE_CELSIUS = "pref_weather_use_celsius"
    const val PREF_WEATHER_CUSTOM_LOCATION = "pref_weather_custom_location"
    const val PREF_WEATHER_CUSTOM_LOCATION_RAW_CACHED = "pref_weather_custom_location_raw_cached"
    const val PREF_WEATHER_CUSTOM_LOCATION_LAT = "pref_weather_custom_location_lat"
    const val PREF_WEATHER_CUSTOM_LOCATION_LON = "pref_weather_custom_location_lon"
    const val PREF_WEATHER_CUSTOM_LOCATION_NAME = "pref_weather_custom_location_name"
    const val WEATHER_CACHE_TTL_MS = 60 * 60 * 1000L
    const val WEATHER_UPDATE_INTERVAL_MINUTES = 60L
    const val WEATHER_UPDATE_TASK_NAME = "weather_updater"
    const val WEATHER_LOCATION_TIMEOUT_MS = 10_000L
    const val WEATHER_LOCATION_STALE_METERS = 5_000f
    const val PREF_SEARCH_CHIP_GRADIENT = "pref_search_chip_gradient"
    const val PREF_SHOW_REALTIME_TRAFFIC_IP = "pref_show_realtime_traffic_ip"
    const val PREF_DISMISS_TEST_BUILD_INFO = "pref_dismiss_test_build_info"

    const val PREF_CUSTOM_PROFILE_NAME = "custom_profile_name"
    const val PREF_PROFILE_BANNER_URI = "profile_banner_uri"
    const val PREF_PROFILE_BANNER_SHAPE = "profile_banner_shape"
    const val PREF_PROFILE_BANNER_SHAPE_DEFAULT = "uwu_shape_cookie_9"
    const val PREF_ACTION_CHANGE_PROFILE_BANNER = "action_change_profile_banner_image"
    const val PREF_ACTION_DELETE_PROFILE_BANNER = "action_delete_profile_banner_image"
    const val PREF_ENABLE_PARTICLES_SHEET = "pref_enable_particles_sheet"
    const val PREF_SHOW_QUICK_ACTIONS = "pref_show_quick_actions"
    const val PREF_ENABLE_SNOWFLAKES = "pref_enable_snowflakes"
    const val PREF_SNOWFLAKES_SPEED = "pref_snowflakes_speed"
    const val PREF_SNOWFLAKES_COUNT = "pref_snowflakes_count"
    const val SNOWFLAKES_SPEED_DEFAULT = 1.0f
    const val SNOWFLAKES_SPEED_MIN = 0.25f
    const val SNOWFLAKES_SPEED_MAX = 3.0f
    const val SNOWFLAKES_COUNT_DEFAULT = 55
    const val SNOWFLAKES_COUNT_MIN = 10
    const val SNOWFLAKES_COUNT_MAX = 120
    const val PREF_SNOWFLAKES_SIZE = "pref_snowflakes_size"
    const val PREF_SNOWFLAKES_OPACITY = "pref_snowflakes_opacity"
    const val PREF_SNOWFLAKES_WIND = "pref_snowflakes_wind"
    const val PREF_SNOWFLAKES_LIFE = "pref_snowflakes_life"
    const val SNOWFLAKES_SIZE_DEFAULT = 1.0f
    const val SNOWFLAKES_SIZE_MIN = 0.5f
    const val SNOWFLAKES_SIZE_MAX = 2.0f
    const val SNOWFLAKES_OPACITY_DEFAULT = 0.60f
    const val SNOWFLAKES_OPACITY_MIN = 0.10f
    const val SNOWFLAKES_OPACITY_MAX = 1.0f
    const val SNOWFLAKES_WIND_DEFAULT = 1.0f
    const val SNOWFLAKES_WIND_MIN = 0.0f
    const val SNOWFLAKES_WIND_MAX = 3.0f
    const val SNOWFLAKES_LIFE_DEFAULT = 15.0f
    const val SNOWFLAKES_LIFE_MIN = 1.0f
    const val SNOWFLAKES_LIFE_MAX = 30.0f
    const val PREF_DISABLE_HOME_BANNER = "pref_disable_home_banner"
    const val PREF_CUSTOM_HOME_BANNER_URI = "custom_home_banner_uri"
    const val PREF_ACTION_CHANGE_HOME_BANNER = "action_change_home_banner_image"
    const val PREF_ACTION_DELETE_HOME_BANNER = "action_delete_home_banner_image"
    const val PREF_CUSTOM_SHEET_BANNER_URI = "custom_sheet_banner_uri"
    const val PREF_ACTION_CHANGE_SHEET_BANNER = "action_change_sheet_banner_image"
    const val PREF_ACTION_DELETE_SHEET_BANNER = "action_delete_sheet_banner_image"
    const val PREF_SHEET_BANNER_DIM = "pref_sheet_banner_dim"
    const val SHEET_BANNER_DIM_DEFAULT = 50
    const val SHEET_BANNER_DIM_MIN = 0
    const val SHEET_BANNER_DIM_MAX = 90
    const val PREF_CUSTOM_THEME_BANNER_URI = "custom_theme_banner_uri"
    const val PREF_BANNER_SETTINGS_CHARACTER = "pref_banner_settings_character"
    const val PREF_BANNER_SETTINGS_CHARACTER_DEFAULT = "uwu_banner_miku_v2"
    const val PREF_CUSTOM_BANNER_SETTINGS_CHARACTER_URI = "custom_banner_settings_character_uri"
    const val PREF_ACTION_DELETE_BANNER_SETTINGS_CHARACTER = "action_delete_banner_settings_character"

    const val PREF_BANNER_CHARACTER_WIDTH = "pref_banner_character_width"
    const val BANNER_CHARACTER_WIDTH_DEFAULT = 220f
    const val BANNER_CHARACTER_WIDTH_MIN = 80f
    const val BANNER_CHARACTER_WIDTH_MAX = 400f

    const val PREF_BANNER_CHARACTER_HEIGHT = "pref_banner_character_height"
    const val BANNER_CHARACTER_HEIGHT_DEFAULT = 250f
    const val BANNER_CHARACTER_HEIGHT_MIN = 80f
    const val BANNER_CHARACTER_HEIGHT_MAX = 400f

    const val PREF_BANNER_CHARACTER_MARGIN_TOP = "pref_banner_character_margin_top"
    const val BANNER_CHARACTER_MARGIN_TOP_DEFAULT = 10f
    const val BANNER_CHARACTER_MARGIN_TOP_MIN = -150f
    const val BANNER_CHARACTER_MARGIN_TOP_MAX = 150f

    const val PREF_BANNER_CHARACTER_MARGIN_BOTTOM = "pref_banner_character_margin_bottom"
    const val BANNER_CHARACTER_MARGIN_BOTTOM_DEFAULT = 0f
    const val BANNER_CHARACTER_MARGIN_BOTTOM_MIN = -150f
    const val BANNER_CHARACTER_MARGIN_BOTTOM_MAX = 150f

    const val PREF_BANNER_CHARACTER_MARGIN_END = "pref_banner_character_margin_end"
    const val BANNER_CHARACTER_MARGIN_END_DEFAULT = -70f
    const val BANNER_CHARACTER_MARGIN_END_MIN = -250f
    const val BANNER_CHARACTER_MARGIN_END_MAX = 150f
    const val PREF_HOME_BANNER_HEIGHT = "pref_home_banner_height"
    const val HOME_BANNER_HEIGHT_DEFAULT = 170
    const val FONT_SIZE_DEFAULT = 1.0f
    const val FONT_SIZE_MIN = 0.50f
    const val FONT_SIZE_MAX = 1.50f
    const val HOME_BANNER_HEIGHT_MIN = 150
    const val HOME_BANNER_HEIGHT_MAX = 300
    const val PREF_HEADER_TOP_ROW_PADDING = "pref_header_top_row_padding"
    const val HEADER_TOP_ROW_PADDING_DEFAULT = 16
    const val HEADER_TOP_ROW_PADDING_MIN = 0
    const val HEADER_TOP_ROW_PADDING_MAX = 100

    const val PREF_DISABLE_SENSOR_TEXT = "pref_disable_sensor_text"
    const val PREF_SELECTED_BANNER_STYLE_ENABLED = "pref_selected_banner_style_enabled"
    const val PREF_SELECTED_BANNER_URI = "custom_selected_banner_uri"
    const val PREF_SELECTED_BANNER_DIM = "pref_selected_banner_dim"
    const val SELECTED_BANNER_DIM_DEFAULT = 60
    const val SELECTED_BANNER_DIM_MIN = 0
    const val SELECTED_BANNER_DIM_MAX = 90
    const val PREF_ACTION_CHANGE_SELECTED_BANNER = "action_change_selected_banner_image"
    const val PREF_ACTION_DELETE_SELECTED_BANNER = "action_delete_selected_banner_image"

    const val PREF_TOOLBAR_CENTER_SUBTITLE_MODE = "pref_toolbar_center_subtitle_mode"

    const val PREF_PARTICLES_SETTINGS = "pref_particles_settings"

    const val PREF_PARTICLES_FRAME_DELAY = "pref_particles_frame_delay"
    const val PARTICLES_FRAME_DELAY_DEFAULT = 10f
    const val PARTICLES_FRAME_DELAY_MIN = 0f
    const val PARTICLES_FRAME_DELAY_MAX = 100f

    const val PREF_PARTICLES_LINE_LENGTH = "pref_particles_line_length"
    const val PARTICLES_LINE_LENGTH_DEFAULT = 86f
    const val PARTICLES_LINE_LENGTH_MIN = 20f
    const val PARTICLES_LINE_LENGTH_MAX = 200f

    const val PREF_PARTICLES_LINE_THICKNESS = "pref_particles_line_thickness"
    const val PARTICLES_LINE_THICKNESS_DEFAULT = 1f
    const val PARTICLES_LINE_THICKNESS_MIN = 1f
    const val PARTICLES_LINE_THICKNESS_MAX = 10f

    const val PREF_PARTICLES_RADIUS_MAX = "pref_particles_radius_max"
    const val PARTICLES_RADIUS_MAX_DEFAULT = 4f
    const val PARTICLES_RADIUS_MAX_MIN = 1f
    const val PARTICLES_RADIUS_MAX_MAX = 20f

    const val PREF_PARTICLES_RADIUS_MIN = "pref_particles_radius_min"
    const val PARTICLES_RADIUS_MIN_DEFAULT = 1f
    const val PARTICLES_RADIUS_MIN_MIN = 1f
    const val PARTICLES_RADIUS_MIN_MAX = 10f

    const val PREF_PARTICLES_DENSITY = "pref_particles_density"
    const val PARTICLES_DENSITY_DEFAULT = 60f
    const val PARTICLES_DENSITY_MIN = 10f
    const val PARTICLES_DENSITY_MAX = 150f

    const val PREF_PARTICLES_SPEED_FACTOR = "pref_particles_speed_factor"
    const val PARTICLES_SPEED_FACTOR_DEFAULT = 1.0f
    const val PARTICLES_SPEED_FACTOR_MIN = 0.1f
    const val PARTICLES_SPEED_FACTOR_MAX = 3.0f

    const val CACHE_SUBSCRIPTION_ID = "cache_subscription_id"

    const val PROTOCOL_FREEDOM = "freedom"

    const val BROADCAST_ACTION_SERVICE = "$ANG_PACKAGE.action.service"
    const val BROADCAST_ACTION_ACTIVITY = "$ANG_PACKAGE.action.activity"
    const val BROADCAST_ACTION_WIDGET_CLICK = "$ANG_PACKAGE.action.widget.click"
    const val BROADCAST_ACTION_TRAFFIC_WIDGET_REFRESH = "$ANG_PACKAGE.action.traffic_widget_refresh"
    const val EXTRA_SHOW_TOTAL_TRAFFIC_DETAIL = "extra_show_total_traffic_detail"

    const val TASKER_EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE"
    const val TASKER_EXTRA_STRING_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB"
    const val TASKER_EXTRA_BUNDLE_SWITCH = "tasker_extra_bundle_switch"
    const val TASKER_EXTRA_BUNDLE_GUID = "tasker_extra_bundle_guid"
    const val TASKER_DEFAULT_GUID = "Default"

    const val TAG_PROXY = "proxy"
    const val TAG_DIRECT = "direct"
    const val TAG_BLOCKED = "block"
    const val TAG_FRAGMENT = "fragment"
    const val TAG_DNS = "dns-module"
    const val TAG_DOMESTIC_DNS = "domestic-dns"
    const val TAG_BALANCER = "balancer-main"
    const val TAG_BALANCER_PRE = "balancer"

    const val UPLINK = "uplink"
    const val DOWNLINK = "downlink"

    const val GITHUB_URL = "https://github.com"
    const val GITHUB_RAW_URL = "https://raw.githubusercontent.com"
    const val GITHUB_DOWNLOAD_URL = "$GITHUB_URL/%s/releases/latest/download"
    const val ANDROID_PACKAGE_NAME_LIST_URL = "$GITHUB_RAW_URL/2dust/androidpackagenamelist/master/proxy.txt"
    const val APP_URL = "$GITHUB_URL/Yuk1Org/MikuBox"
    const val APP_API_URL = "https://api.github.com/repos/Yuk1Org/MikuBox/releases"
    const val APP_ISSUES_URL = "$APP_URL/issues"
    const val APP_WIKI_MODE = "$APP_URL/wiki/Mode"
    const val APP_PRIVACY_POLICY = "$GITHUB_RAW_URL/HatsuneMikuUwU/MikuRay/master/CR.md"
    const val APP_PROMOTION_URL = "aHR0cHM6Ly85LjIzNDQ1Ni54eXovYWJjLmh0bWw="
    const val TG_CHANNEL_URL = "https://t.me/uwuowoumuchannel"
    const val DELAY_TEST_URL = "https://www.gstatic.com/generate_204"
    const val DELAY_TEST_URL2 = "https://www.google.com/generate_204"
    const val OBSERVATORY_LEAST_PING_INTERVAL = "3m"
    const val OBSERVATORY_LEAST_LOAD_INTERVAL = "5m"
    const val OBSERVATORY_LEAST_LOAD_METHOD = "HEAD"
    const val OBSERVATORY_LEAST_LOAD_SAMPLING = "2"
    const val OBSERVATORY_LEAST_LOAD_TIMEOUT = "30s"

    const val IP_API_URL = "https://api.ip.sb/geoip"

    const val DEFAULT_FAKE_DNS_IP_POOL = "198.18.0.0/15"
    const val FAKE_DNS_MAX_POOL_SIZE = 65_535

    const val DNS_PROXY = "https://cloudflare-dns.com/dns-query"
    const val DNS_DIRECT = "223.5.5.5"
    const val DNS_VPN = "1.1.1.1"
    const val GEOSITE_PRIVATE = "geosite:private"
    const val GEOSITE_CN = "geosite:cn"
    const val GEOIP_PRIVATE = "geoip:private"
    const val GEOIP_CN = "geoip:cn"

    const val GEOSITE_DAT = "geosite.dat"
    const val GEOIP_DAT = "geoip.dat"
    const val GEOIP_ONLY_CN_PRIVATE_DAT = "geoip-only-cn-private.dat"
    const val GEOIP_ONLY_CN_PRIVATE_URL = "$GITHUB_RAW_URL/Loyalsoldier/geoip/release/$GEOIP_ONLY_CN_PRIVATE_DAT"

    const val PORT_LOCAL_DNS = "10853"
    const val PORT_SOCKS = "10808"
    const val WIREGUARD_LOCAL_ADDRESS_V4 = "172.16.0.2/32"
    const val WIREGUARD_LOCAL_ADDRESS_V6 = "2606:4700:110:8f81:d551:a0:532e:a2b3/128"
    const val WIREGUARD_LOCAL_MTU = "1420"
    const val LOOPBACK = "127.0.0.1"

    const val MSG_REGISTER_CLIENT = 1
    const val MSG_STATE_RUNNING = 11
    const val MSG_STATE_NOT_RUNNING = 12
    const val MSG_UNREGISTER_CLIENT = 2
    const val MSG_STATE_START = 3
    const val MSG_STATE_START_SUCCESS = 31
    const val MSG_STATE_START_FAILURE = 32
    const val MSG_STATE_STOP = 4
    const val MSG_STATE_STOP_SUCCESS = 41
    const val MSG_STATE_RESTART = 5
    const val MSG_MEASURE_DELAY = 6
    const val MSG_MEASURE_DELAY_SUCCESS = 61
    const val MSG_MEASURE_DELAY_CANCEL = 64
    const val MSG_MEASURE_IP = 62
    const val MSG_MEASURE_IP_SUCCESS = 63
    const val MSG_MEASURE_CONFIG_START = 7
    const val MSG_MEASURE_CONFIG_CANCEL = 71
    const val MSG_MEASURE_CONFIG_SUCCESS = 72
    const val MSG_MEASURE_CONFIG_NOTIFY = 73
    const val MSG_MEASURE_CONFIG_FINISH = 74
    const val MSG_COUNTRY_CODE_START = 90
    const val MSG_COUNTRY_CODE_CANCEL = 91
    const val MSG_COUNTRY_CODE_SUCCESS = 92
    const val MSG_COUNTRY_CODE_NOTIFY = 93
    const val MSG_COUNTRY_CODE_FINISH = 94

    const val MSG_SUB_UPDATE_START = 8
    const val MSG_SUB_UPDATE_CANCEL = 81
    const val MSG_TRAFFIC_UPDATED = 84
    const val MSG_TRAFFIC_SPEED_UPDATED = 82
    const val MSG_SUB_UPDATE_FINISH = 83

    const val RAY_NG_CHANNEL_ID = "CORE_M_CH_ID_V2"
    const val RAY_NG_CHANNEL_NAME = "Core Background Service"

    const val VMESS = "vmess://"
    const val CUSTOM = ""
    const val SHADOWSOCKS = "ss://"
    const val SOCKS = "socks://"
    const val SOCKS4 = "socks4://"
    const val SOCKS5 = "socks5://"
    const val HTTP = "http://"
    const val VLESS = "vless://"
    const val TROJAN = "trojan://"
    const val WIREGUARD = "wireguard://"
    const val TUIC = "tuic://"
    const val HYSTERIA = "hysteria://"
    const val HYSTERIA2 = "hysteria2://"
    const val HY2 = "hy2://"
    const val V2RAYNFMTS = "v2rayn://"

    const val VPN = "VPN"
    const val VPN_MTU = 1500

    const val ROOT_RUNTIME_DIR = "sys_cache"
    const val ROOT_IPTABLES_CHAIN = "CORE_FILTER"
    const val ROOT_FWMARK = 255
    const val ROOT_MARK_ROUTE = 1
    const val ROOT_ROUTE_TABLE = 2024
    const val ROOT_RULE_PRIORITY = 1000
    const val ROOT_TUN_NAME = "utun7788"
    const val ROOT_TUN_ADDR_V4 = "198.18.0.1/15"
    const val ROOT_TUN_ADDR_V6 = "fdfe:dcba:9876::1/64"
    const val ROOT_TUN2SOCKS_BIN = "libhevsockstun.so"
    const val ROOT_FWD_CHAIN = "CORE_FWD"
    const val ROOT_DNS_CHAIN = "CORE_DNS"
    const val ROOT_V6_CHAIN = "CORE6_FILTER"
    const val ROOT_V6_FWD_CHAIN = "CORE6_FWD"
    const val ROOT_V6_PRE_CHAIN = "CORE6_PRE"
    const val ROOT_LAN_DNS = "1.1.1.1"
    const val ROOT_OOM_SCORE = "-1000"

    const val HEVTUN_RW_TIMEOUT = "300,60"

    const val GOOGLEAPIS_CN_DOMAIN = "domain:googleapis.cn"
    const val GOOGLEAPIS_COM_DOMAIN = "googleapis.com"

    const val DNS_ALIDNS_DOMAIN = "dns.alidns.com"
    const val DNS_CISCO_SSE_DOMAIN = "dns.sse.cisco.com"
    const val DNS_CISCO_UMBRELLA_DOMAIN = "dns.umbrella.com"
    const val DNS_CLOUDFLARE_ONE_DOMAIN = "one.one.one.one"
    const val DNS_CLOUDFLARE_ONEDOT_DNS_DOMAIN = "1dot1dot1dot1.cloudflare-dns.com"
    const val DNS_CLOUDFLARE_DNS_COM_DOMAIN = "dns.cloudflare.com"
    const val DNS_CLOUDFLARE_DNS_DOMAIN = "cloudflare-dns.com"
    const val DNS_CLOUDFLARE_WARP_DOMAIN = "engage.cloudflareclient.com"
    const val DNS_DNSPOD_DOH_DOMAIN = "doh.pub"
    const val DNS_DNSPOD_DOT_DOMAIN = "dot.pub"
    const val DNS_GOOGLE_DOMAIN = "dns.google"
    const val DNS_QUAD9_DOMAIN = "dns.quad9.net"
    const val DNS_SB_DOMAIN = "dns.sb"
    const val DNS_YANDEX_DOMAIN = "common.dot.dns.yandex.net"

    const val DEFAULT_PORT = 443
    const val DEFAULT_SECURITY = "auto"
    const val DEFAULT_LEVEL = 8
    const val DEFAULT_NETWORK = "tcp"
    const val TLS = "tls"
    const val REALITY = "reality"
    const val HEADER_TYPE_HTTP = "http"

    const val UNIDENTIFIED_PACKAGE = "__unknown_app__"

    val DNS_ALIDNS_ADDRESSES = arrayListOf("223.5.5.5", "223.6.6.6", "2400:3200::1", "2400:3200:baba::1")
    val DNS_CISCO_SSE_ADDRESSES = arrayListOf("208.67.220.220", "208.67.222.222", "2620:119:35::35", "2620:119:53::53")
    val DNS_CISCO_UMBRELLA_ADDRESSES = arrayListOf("208.67.220.220", "208.67.222.222", "2620:119:35::35", "2620:119:53::53")
    val DNS_CLOUDFLARE_ONE_ADDRESSES = arrayListOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "2606:4700:4700::1001")
    val DNS_CLOUDFLARE_ONEDOT_DNS_ADDRESSES = arrayListOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "2606:4700:4700::1001")
    val DNS_CLOUDFLARE_DNS_COM_ADDRESSES = arrayListOf("162.159.61.8", "172.64.41.8", "2a06:98c1:52::8", "2803:f800:53::8")
    val DNS_CLOUDFLARE_DNS_ADDRESSES = arrayListOf("104.16.248.249", "104.16.249.249", "2606:4700::6810:f8f9", "2606:4700::6810:f9f9")
    val DNS_CLOUDFLARE_WARP_ADDRESSES = arrayListOf("162.159.192.1", "2606:4700:d0::a29f:c001")
    val DNS_DNSPOD_DOH_ADDRESSES = arrayListOf("1.12.12.12", "120.53.53.53")
    val DNS_DNSPOD_DOT_ADDRESSES = arrayListOf("1.12.12.12", "120.53.53.53")
    val DNS_GOOGLE_ADDRESSES = arrayListOf("8.8.8.8", "8.8.4.4", "2001:4860:4860::8888", "2001:4860:4860::8844")
    val DNS_QUAD9_ADDRESSES = arrayListOf("9.9.9.9", "149.112.112.112", "2620:fe::fe", "2620:fe::9")
    val DNS_SB_ADDRESSES = arrayListOf("45.11.45.11", "185.222.222.222", "2a09::", "2a11::")
    val DNS_YANDEX_ADDRESSES = arrayListOf("77.88.8.8", "77.88.8.1", "2a02:6b8::feed:0ff", "2a02:6b8:0:1::feed:0ff")

    val ROUTED_IP_LIST = arrayListOf(
        "0.0.0.0/5",
        "8.0.0.0/7",
        "11.0.0.0/8",
        "12.0.0.0/6",
        "16.0.0.0/4",
        "32.0.0.0/3",
        "64.0.0.0/2",
        "128.0.0.0/3",
        "160.0.0.0/5",
        "168.0.0.0/6",
        "172.0.0.0/12",
        "172.32.0.0/11",
        "172.64.0.0/10",
        "172.128.0.0/9",
        "173.0.0.0/8",
        "174.0.0.0/7",
        "176.0.0.0/4",
        "192.0.0.0/9",
        "192.128.0.0/11",
        "192.160.0.0/13",
        "192.169.0.0/16",
        "192.170.0.0/15",
        "192.172.0.0/14",
        "192.176.0.0/12",
        "192.192.0.0/10",
        "193.0.0.0/8",
        "194.0.0.0/7",
        "196.0.0.0/6",
        "200.0.0.0/5",
        "208.0.0.0/4",
        "240.0.0.0/4"
    )

    val PRIVATE_IP_LIST = arrayListOf(
        "0.0.0.0/8",
        "10.0.0.0/8",
        "127.0.0.0/8",
        "172.16.0.0/12",
        "192.168.0.0/16",
        "169.254.0.0/16",
        "224.0.0.0/4"
    )

    val GEO_FILES_SOURCES = arrayListOf(
        "Loyalsoldier/v2ray-rules-dat",
        "runetfreedom/russia-v2ray-rules-dat",
        "Chocolate4U/Iran-v2ray-rules"
    )

    val BUILTIN_OUTBOUND_TAGS = setOf(
        TAG_PROXY,
        TAG_DIRECT,
        TAG_BLOCKED,
    )

    val OBSERVATORY_DURATION_PATTERN = Regex("""[1-9]\d*(ms|s|m|h)""")

    const val EXTRA_HIGHLIGHT_KEY = "extra_highlight_preference_key"
}
