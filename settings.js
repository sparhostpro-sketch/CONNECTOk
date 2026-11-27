// CONNECTO - Settings System

const DEFAULT_SETTINGS = {
    theme: "light",
    language: "ar",
    notifications: true,
    sound: true,
    privacy: "public",
    fontSize: "medium"
};

function getSettings() {
    try {
        const saved = localStorage.getItem("connecto_settings");

        return saved
            ? { ...DEFAULT_SETTINGS, ...JSON.parse(saved) }
            : { ...DEFAULT_SETTINGS };
    } catch {
        return { ...DEFAULT_SETTINGS };
    }
}

function saveSettings(settings) {
    const current = getSettings();

    const updated = {
        ...current,
        ...settings
    };

    localStorage.setItem(
        "connecto_settings",
        JSON.stringify(updated)
    );

    applySettings(updated);

    return updated;
}

function applySettings(settings = getSettings()) {
    document.documentElement.lang = settings.language;
    document.documentElement.dir =
        settings.language === "ar" ? "rtl" : "ltr";

    document.documentElement.dataset.theme = settings.theme;

    document.body.classList.toggle(
        "dark-mode",
        settings.theme === "dark"
    );
}

function resetSettings() {
    localStorage.removeItem("connecto_settings");
    applySettings(DEFAULT_SETTINGS);

    return { ...DEFAULT_SETTINGS };
}

document.addEventListener("DOMContentLoaded", () => {
    applySettings();
});
