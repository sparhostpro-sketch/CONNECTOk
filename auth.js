// CONNECTO - Authentication Interface

function validateRegistration(name, email, password) {
    if (!name || name.trim().length < 3) {
        return "الاسم يجب أن يكون 3 أحرف على الأقل";
    }

    if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        return "البريد الإلكتروني غير صحيح";
    }

    if (!password || password.length < 8) {
        return "كلمة المرور يجب أن تكون 8 أحرف على الأقل";
    }

    return null;
}

function validateLogin(email, password) {
    if (!email || !password) {
        return "من فضلك أدخل البريد الإلكتروني وكلمة المرور";
    }

    return null;
}

function createUserProfile(name, email) {
    return {
        name: name.trim(),
        email: email.trim().toLowerCase(),
        avatar: "",
        bio: "",
        createdAt: new Date().toISOString()
    };
}

async function registerUser(name, email, password) {
    const error = validateRegistration(name, email, password);

    if (error) {
        throw new Error(error);
    }

    throw new Error(
        "التسجيل الحقيقي يحتاج إلى ربط CONNECTO بخادم آمن"
    );
}

async function loginUser(email, password) {
    const error = validateLogin(email, password);

    if (error) {
        throw new Error(error);
    }

    throw new Error(
        "تسجيل الدخول يحتاج إلى خادم مصادقة آمن"
    );
}

function logoutUser() {
    sessionStorage.removeItem("connecto_session");
}
