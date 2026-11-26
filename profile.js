
// CONNECTO - User Profile System

async function createProfile(userId, name, bio = "") {
    if (!userId || !name) {
        throw new Error("بيانات الحساب غير مكتملة");
    }

    const profile = {
        userId,
        name: name.trim(),
        bio: bio.trim(),
        avatar: "",
        cover: "",
        followers: [],
        following: [],
        createdAt: new Date().toISOString()
    };

    return await saveData("users", profile);
}

async function getProfile(userId) {
    const users = await getAllData("users");

    return users.find(user => user.userId === userId) || null;
}

async function updateProfile(userId, updates) {
    const db = await openDatabase();

    return new Promise((resolve, reject) => {
        const transaction = db.transaction("users", "readwrite");
        const store = transaction.objectStore("users");
        const request = store.getAll();

        request.onsuccess = function() {
            const users = request.result;

            const profile = users.find(
                user => user.userId === userId
            );

            if (!profile) {
                reject(new Error("الحساب غير موجود"));
                return;
            }

            profile.name = updates.name?.trim() || profile.name;
            profile.bio = updates.bio?.trim() ?? profile.bio;
            profile.avatar = updates.avatar ?? profile.avatar;
            profile.cover = updates.cover ?? profile.cover;

            store.put(profile);
            resolve(profile);
        };

        request.onerror = () => reject(request.error);
    });
}

async function followUser(currentUserId, targetUserId) {
    if (currentUserId === targetUserId) {
        throw new Error("لا يمكنك متابعة نفسك");
    }

    const db = await openDatabase();

    return new Promise((resolve, reject) => {
        const transaction = db.transaction("users", "readwrite");
        const store = transaction.objectStore("users");
        const request = store.getAll();

        request.onsuccess = function() {
            const users = request.result;

            const current = users.find(
                user => user.userId === currentUserId
            );

            const target = users.find(
                user => user.userId === targetUserId
            );

            if (!current || !target) {
                reject(new Error("المستخدم غير موجود"));
                return;
            }

            current.following = current.following || [];
            target.followers = target.followers || [];

            if (!current.following.includes(targetUserId)) {
                current.following.push(targetUserId);
                target.followers.push(currentUserId);
            }

            store.put(current);
            store.put(target);

            resolve(true);
        };

        request.onerror = () => reject(request.error);
    });
}
