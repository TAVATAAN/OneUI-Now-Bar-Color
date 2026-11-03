package io.github.tavataan.nowbarcolor;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Locale;

public class I18n {

    public static final String PREF_NAME = "nowbar_prefs";
    public static final String PREF_KEY_LANG = "app_language";

    public static final String LANG_AUTO = "auto";
    public static final String LANG_EN = "en";
    public static final String LANG_ES = "es";
    public static final String LANG_PT = "pt";

    private static String sCurrentLang = null;

    public static String getLanguage(Context ctx) {
        if (sCurrentLang != null) {
            return sCurrentLang;
        }
        if (ctx != null) {
            SharedPreferences prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            sCurrentLang = prefs.getString(PREF_KEY_LANG, LANG_AUTO);
        } else {
            sCurrentLang = LANG_AUTO;
        }
        return sCurrentLang;
    }

    public static void setLanguage(Context ctx, String lang) {
        sCurrentLang = lang;
        if (ctx != null) {
            SharedPreferences prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(PREF_KEY_LANG, lang).apply();
        }
    }

    public static String getEffectiveLanguage(Context ctx) {
        String lang = getLanguage(ctx);
        if (LANG_AUTO.equals(lang)) {
            String sys = Locale.getDefault().getLanguage().toLowerCase(Locale.ROOT);
            if (sys.startsWith("es")) return LANG_ES;
            if (sys.startsWith("pt")) return LANG_PT;
            return LANG_EN;
        }
        if (LANG_ES.equals(lang)) return LANG_ES;
        if (LANG_PT.equals(lang)) return LANG_PT;
        return LANG_EN;
    }

    public static String get(Context ctx, String key) {
        String lang = getEffectiveLanguage(ctx);
        switch (lang) {
            case LANG_ES:
                return getEs(key);
            case LANG_PT:
                return getPt(key);
            case LANG_EN:
            default:
                return getEn(key);
        }
    }

    private static String getEn(String key) {
        switch (key) {
            case "app_title": return "Now Bar Color";
            case "app_subtitle": return "Customize only the background of the collapsed Now Bar capsule on the lock screen without altering photos, icons, or text.";
            case "enable_title": return "Enable Now Bar Customization";
            case "enable_sub": return "Applies changes instantly on the lock screen without restarting SystemUI";
            case "preview_title": return "REAL-TIME PREVIEW (MULTIMEDIA NOW BAR)";
            case "preview_original": return "Original One UI";
            case "preview_solid": return "Solid";
            case "preview_blur": return "Blur";
            case "preview_gradient": return "Gradient";
            case "sec_color_mode": return "CAPSULE BACKGROUND COLOR MODE";
            case "tab_solid": return "Solid Color";
            case "tab_wallpaper": return "Wallpaper Color";
            case "tab_gradient": return "Gradient";
            case "palette_label": return "Select a solid color from the palette or create a custom one:";
            case "dialog_global_color": return "Now Bar Global Color";
            case "wall_info": return "The capsule color automatically synchronizes with your current lock screen wallpaper:";
            case "wall_primary_title": return "Primary Color (Dominant) — Recommended";
            case "wall_primary_desc": return "Primary tone extracted from lock screen wallpaper";
            case "wall_secondary_title": return "Secondary Background Color";
            case "wall_secondary_desc": return "Secondary complementary tone from lock wallpaper";
            case "wall_monet_title": return "Dynamic Monet Accent (One UI)";
            case "wall_monet_desc": return "System adaptive palette based on your wallpaper";
            case "wall_bottom_title": return "Bottom Zone (Exact Now Bar Area)";
            case "wall_bottom_desc": return "Average color of the lower region where the capsule rests";
            case "wall_updated_toast": return "Lock screen wallpaper colors updated";
            case "grad_info": return "Two-stop horizontal linear gradient (from left to right):";
            case "grad_color_start": return "Start Gradient Color";
            case "grad_color_end": return "End Gradient Color";
            case "sec_finish_opacity": return "VISUAL FINISH AND CAPSULE OPACITY";
            case "finish_solid_title": return "Solid Pure Color";
            case "finish_solid_desc": return "Clear color fill without glass blur";
            case "finish_blur_title": return "Color Blur";
            case "finish_blur_desc": return "Dynamic translucent blur with subtle glass effect";
            case "opacity_title": return "Capsule background opacity";
            case "sec_catalog_title": return "NOW BAR CAPSULES CATALOG (TOGGLE AND INDIVIDUAL COLOR)";
            case "sec_catalog_desc": return "Enable or disable each capsule type. If disabled, it will keep Samsung's original background. Tap any capsule's color button to assign an exclusive color different from the global one.";
            case "affected_services": return "AFFECTED APPS AND SERVICES";
            case "individual_color_title": return "Individual background color";
            case "reset_global_btn": return "Reset to Global Color";
            case "using_global_color": return "Using Global Color and Opacity";
            case "individual_preview_title": return "INDIVIDUAL REAL-TIME PREVIEW";
            case "dialog_color_opacity_prefix": return "Color and Opacity: ";
            case "cancel": return "Cancel";
            case "apply": return "Apply";
            case "apply_color": return "Apply Color";
            case "reset_success_toast": return "Restored to Global Color";
            case "select_lang_title": return "Application Language";
            case "lang_auto": return "Auto (System)";
            case "lang_en": return "English";
            case "lang_es": return "Spanish (Español)";
            case "lang_pt": return "Portuguese (Português)";
            case "picker_current_color": return "Current color";
            case "picker_saturation": return "Saturation";
            case "picker_opacity": return "Opacity: ";
            case "picker_advanced": return "Advanced Picker (HSV + HEX)";
            case "wall_refresh_btn": return "Rescan Lock Screen Wallpaper";
            case "btn_advanced": return "Advanced";
            // Categories
            case "cat_media_title": return "Music & Multimedia";
            case "cat_media_desc": return "Spotify, YouTube Music, Samsung Music, and audio/video players";
            case "cat_timer_title": return "Clock, Timer & Stopwatch";
            case "cat_timer_desc": return "Countdown timers, stopwatch, and Clock alarms";
            case "cat_recorder_title": return "Voice Recorder";
            case "cat_recorder_desc": return "Ongoing voice notes and audio recordings on lock screen";
            case "cat_flashlight_title": return "Flashlight";
            case "cat_flashlight_desc": return "Active flashlight capsule and quick controls on lock screen";
            case "cat_battery_title": return "Battery & Charging";
            case "cat_battery_desc": return "Battery status capsule and fast charging animation";
            case "cat_brief_title": return "Now Brief & Galaxy AI";
            case "cat_brief_desc": return "Samsung intelligent daily summary, weather, and Galaxy AI suggestions";
            case "cat_call_title": return "Ongoing Phone Calls";
            case "cat_call_desc": return "Active call capsule, phone, and background VoIP calls";
            case "cat_modes_title": return "Modes & Routines";
            case "cat_modes_desc": return "Sleep, Driving, Exercise, Work modes, and Bixby Routines";
            case "cat_sports_title": return "Sports, Maps & Health";
            case "cat_sports_desc": return "Google sports scores, live navigation, and Samsung Health";
            case "cat_other_title": return "Other Live Activities";
            case "cat_other_desc": return "Interpreter, Smart View, live sharing, contacts, and third-party apps";
            default: return key;
        }
    }

    private static String getEs(String key) {
        switch (key) {
            case "app_title": return "Now Bar Color";
            case "app_subtitle": return "Personaliza únicamente el fondo de la cápsula Now Bar contraída en la pantalla de bloqueo sin alterar fotos, iconos ni textos.";
            case "enable_title": return "Activar personalización de Now Bar";
            case "enable_sub": return "Aplica los cambios al instante en la pantalla de bloqueo sin reiniciar SystemUI";
            case "preview_title": return "VISTA PREVIA EN TIEMPO REAL (NOW BAR MULTIMEDIA)";
            case "preview_original": return "Original One UI";
            case "preview_solid": return "Sólido";
            case "preview_blur": return "Blur";
            case "preview_gradient": return "Degradado";
            case "sec_color_mode": return "MODO DE COLOR DEL FONDO DE LA CÁPSULA";
            case "tab_solid": return "Color Sólido";
            case "tab_wallpaper": return "Color del Wallpaper";
            case "tab_gradient": return "Degradado";
            case "palette_label": return "Selecciona un color sólido de la paleta o crea uno a medida:";
            case "dialog_global_color": return "Color Global de Now Bar";
            case "wall_info": return "El color de la cápsula se sincroniza automáticamente con tu fondo de pantalla de bloqueo actual:";
            case "wall_primary_title": return "Color Principal (Dominante) — Recomendado";
            case "wall_primary_desc": return "Tono principal extraído del fondo de pantalla de bloqueo";
            case "wall_secondary_title": return "Color Secundario del Fondo";
            case "wall_secondary_desc": return "Tono complementario secundario del fondo de bloqueo";
            case "wall_monet_title": return "Acento Dinámico Monet (One UI)";
            case "wall_monet_desc": return "Paleta adaptativa del sistema basada en tu fondo";
            case "wall_bottom_title": return "Zona Inferior (Área exacta de la Now Bar)";
            case "wall_bottom_desc": return "Color promedio de la región inferior donde se ubica la cápsula";
            case "wall_updated_toast": return "Colores del fondo de bloqueo actualizados";
            case "grad_info": return "Degradado lineal horizontal de dos paradas (de izquierda a derecha):";
            case "grad_color_start": return "Primer Color del Degradado";
            case "grad_color_end": return "Color Final del Degradado";
            case "sec_finish_opacity": return "ACABADO VISUAL Y OPACIDAD DE LA CÁPSULA";
            case "finish_solid_title": return "Color Sólido Puro";
            case "finish_solid_desc": return "Relleno de color nítido sin desenfoque de cristal";
            case "finish_blur_title": return "Color Blur";
            case "finish_blur_desc": return "Desenfoque translúcido dinámico con sutil efecto cristal";
            case "opacity_title": return "Opacidad del fondo de la cápsula";
            case "sec_catalog_title": return "CATÁLOGO DE CÁPSULAS NOW BAR (ACTIVACIÓN Y COLOR INDIVIDUAL)";
            case "sec_catalog_desc": return "Activa o desactiva cada tipo de cápsula. Si está desactivada, conservará el fondo original de Samsung. Toca el botón de color de cualquier cápsula si deseas asignarle un color exclusivo distinto al global.";
            case "affected_services": return "APLICACIONES Y SERVICIOS AFECTADOS";
            case "individual_color_title": return "Color de fondo individual";
            case "reset_global_btn": return "Restablecer a Color Global";
            case "using_global_color": return "Usando Color y Opacidad Global";
            case "individual_preview_title": return "VISTA PREVIA INDIVIDUAL EN TIEMPO REAL";
            case "dialog_color_opacity_prefix": return "Color y Opacidad: ";
            case "cancel": return "Cancelar";
            case "apply": return "Aplicar";
            case "apply_color": return "Aplicar Color";
            case "reset_success_toast": return "Restablecido al Color Global";
            case "select_lang_title": return "Idioma de la Aplicación";
            case "lang_auto": return "Automático (Sistema)";
            case "lang_en": return "Inglés (English)";
            case "lang_es": return "Español";
            case "lang_pt": return "Portugués (Português)";
            case "picker_current_color": return "Color actual";
            case "picker_saturation": return "Saturación";
            case "picker_opacity": return "Opacidad: ";
            case "picker_advanced": return "Selector Avanzado (HSV + HEX)";
            case "wall_refresh_btn": return "Re-escanear fondo de pantalla de bloqueo";
            case "btn_advanced": return "Avanzado";
            // Categories
            case "cat_media_title": return "Música y Multimedia";
            case "cat_media_desc": return "Spotify, YouTube Music, Samsung Music y reproductores de audio/video";
            case "cat_timer_title": return "Reloj, Temporizador y Cronómetro";
            case "cat_timer_desc": return "Temporizadores en cuenta regresiva, cronómetro y alarmas de Reloj";
            case "cat_recorder_title": return "Grabadora de Voz";
            case "cat_recorder_desc": return "Notas de voz y grabaciones de audio en curso en pantalla de bloqueo";
            case "cat_flashlight_title": return "Linterna";
            case "cat_flashlight_desc": return "Cápsula de linterna activa y controles rápidos en pantalla de bloqueo";
            case "cat_battery_title": return "Batería y Carga";
            case "cat_battery_desc": return "Cápsula de estado de batería y animación de carga rápida";
            case "cat_brief_title": return "Now Brief e Inteligencia Artificial";
            case "cat_brief_desc": return "Resumen diario inteligente de Samsung, clima y sugerencias Galaxy AI";
            case "cat_call_title": return "Llamadas en Curso";
            case "cat_call_desc": return "Cápsula de llamada activa, teléfono y llamadas VoIP en segundo plano";
            case "cat_modes_title": return "Modos y Rutinas";
            case "cat_modes_desc": return "Modo Sueño, Conducción, Ejercicio, Trabajo y Rutinas de Bixby";
            case "cat_sports_title": return "Deportes, Mapas y Salud";
            case "cat_sports_desc": return "Marcadores deportivos de Google, navegación en vivo y Samsung Health";
            case "cat_other_title": return "Otras Actividades en Vivo";
            case "cat_other_desc": return "Intérprete, Smart View, compartición en vivo, contactos y apps de terceros";
            default: return key;
        }
    }

    private static String getPt(String key) {
        switch (key) {
            case "app_title": return "Now Bar Color";
            case "app_subtitle": return "Personalize apenas o fundo da cápsula Now Bar recolhida na tela de bloqueio sem alterar fotos, ícones ou textos.";
            case "enable_title": return "Ativar personalização da Now Bar";
            case "enable_sub": return "Aplica as alterações instantaneamente na tela de bloqueio sem reiniciar a SystemUI";
            case "preview_title": return "PRÉ-VISUALIZAÇÃO EM TEMPO REAL (NOW BAR MULTIMÍDIA)";
            case "preview_original": return "Original One UI";
            case "preview_solid": return "Sólido";
            case "preview_blur": return "Blur";
            case "preview_gradient": return "Gradiente";
            case "sec_color_mode": return "MODO DE COR DE FUNDO DA CÁPSULA";
            case "tab_solid": return "Cor Sólida";
            case "tab_wallpaper": return "Cor do Papel de Parede";
            case "tab_gradient": return "Gradiente";
            case "palette_label": return "Selecione uma cor sólida da paleta ou crie uma personalizada:";
            case "dialog_global_color": return "Cor Global da Now Bar";
            case "wall_info": return "A cor da cápsula é sincronizada automaticamente com o seu papel de parede de bloqueio atual:";
            case "wall_primary_title": return "Cor Principal (Dominante) — Recomendado";
            case "wall_primary_desc": return "Tom principal extraído do papel de parede de bloqueio";
            case "wall_secondary_title": return "Cor Secundária do Fundo";
            case "wall_secondary_desc": return "Tom complementar secundário do papel de parede de bloqueio";
            case "wall_monet_title": return "Acento Dinâmico Monet (One UI)";
            case "wall_monet_desc": return "Paleta adaptativa do sistema baseada no seu papel de parede";
            case "wall_bottom_title": return "Zona Inferior (Área exata da Now Bar)";
            case "wall_bottom_desc": return "Cor média da região inferior onde a cápsula repousa";
            case "wall_updated_toast": return "Cores do papel de parede de bloqueio atualizadas";
            case "grad_info": return "Gradiente linear horizontal de dois pontos (da esquerda para a direita):";
            case "grad_color_start": return "Primeira Cor do Gradiente";
            case "grad_color_end": return "Cor Final do Gradiente";
            case "sec_finish_opacity": return "ACABAMENTO VISUAL E OPACIDADE DA CÁPSULA";
            case "finish_solid_title": return "Cor Sólida Pura";
            case "finish_solid_desc": return "Preenchimento de cor nítida sem desfoque de vidro";
            case "finish_blur_title": return "Color Blur";
            case "finish_blur_desc": return "Desfoque translúcido dinâmico com sutil efeito de vidro";
            case "opacity_title": return "Opacidade do fundo da cápsula";
            case "sec_catalog_title": return "CATÁLOGO DE CÁPSULAS NOW BAR (ATIVAÇÃO E COR INDIVIDUAL)";
            case "sec_catalog_desc": return "Ative ou desative cada tipo de cápsula. Se desativada, manterá o fundo original da Samsung. Toque no botão de cor de cualquier cápsula para atribuir uma cor exclusiva diferente da global.";
            case "affected_services": return "APLICATIVOS E SERVIÇOS AFETADOS";
            case "individual_color_title": return "Cor de fundo individual";
            case "reset_global_btn": return "Redefinir para a Cor Global";
            case "using_global_color": return "Usando Cor e Opacidade Global";
            case "individual_preview_title": return "PRÉ-VISUALIZAÇÃO INDIVIDUAL EM TEMPO REAL";
            case "dialog_color_opacity_prefix": return "Cor e Opacidade: ";
            case "cancel": return "Cancelar";
            case "apply": return "Aplicar";
            case "apply_color": return "Aplicar Cor";
            case "reset_success_toast": return "Restaurado para a Cor Global";
            case "select_lang_title": return "Idioma do Aplicativo";
            case "lang_auto": return "Automático (Sistema)";
            case "lang_en": return "Inglês (English)";
            case "lang_es": return "Espanhol (Español)";
            case "lang_pt": return "Português";
            case "picker_current_color": return "Cor atual";
            case "picker_saturation": return "Saturação";
            case "picker_opacity": return "Opacidade: ";
            case "picker_advanced": return "Seletor Avançado (HSV + HEX)";
            case "wall_refresh_btn": return "Escanear papel de parede novamente";
            case "btn_advanced": return "Avançado";
            // Categories
            case "cat_media_title": return "Música e Multimídia";
            case "cat_media_desc": return "Spotify, YouTube Music, Samsung Music e reprodutores de áudio/vídeo";
            case "cat_timer_title": return "Relógio, Temporizador e Cronômetro";
            case "cat_timer_desc": return "Temporizadores regressivos, cronômetro e alarmes do Relógio";
            case "cat_recorder_title": return "Gravador de Voz";
            case "cat_recorder_desc": return "Notas de voz e gravações de áudio em andamento na tela de bloqueio";
            case "cat_flashlight_title": return "Lanterna";
            case "cat_flashlight_desc": return "Cápsula de lanterna ativa e controles rápidos na tela de bloqueio";
            case "cat_battery_title": return "Bateria e Carregamento";
            case "cat_battery_desc": return "Cápsula de status da bateria e animação de carregamento rápido";
            case "cat_brief_title": return "Now Brief e Galaxy AI";
            case "cat_brief_desc": return "Resumo diário inteligente da Samsung, clima e sugestões Galaxy AI";
            case "cat_call_title": return "Chamadas em Andamento";
            case "cat_call_desc": return "Cápsula de chamada ativa, telefone e chamadas VoIP em segundo plano";
            case "cat_modes_title": return "Modos e Rotinas";
            case "cat_modes_desc": return "Modo Sono, Direção, Exercício, Trabalho e Rotinas da Bixby";
            case "cat_sports_title": return "Esportes, Mapas e Saúde";
            case "cat_sports_desc": return "Placares esportivos do Google, navegação ao vivo e Samsung Health";
            case "cat_other_title": return "Outras Atividades ao Vivo";
            case "cat_other_desc": return "Intérprete, Smart View, compartilhamento ao vivo, contatos e apps de terceiros";
            default: return key;
        }
    }
}
