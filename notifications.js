// CONNECTO - Notifications System

async function createNotification(userId, type, message, fromUser = "") {
    if (!userId || !message) {
        throw new Error("بيانات الإشعار غير مكتملة");
    }

    const notification = {
        userId,
        type,
        message,
        fromUser,
        read: false,
        createdAt: new Date().toISOString()
    };

    return await saveData("notifications", notification);
}

async function getNotifications(userId) {
    const db = await openDatabase();

    return new Promise((resolve, reject) => {
        const transaction = db.transaction(
            "notifications",
            "readonly"
        );

        const request = transaction
            .objectStore("notifications")
            .getAll();

        request.onsuccess = () => {
            const result = request.result
                .filter(item => item.userId === userId)
                .sort((a, b) =>
                    new Date(b.createdAt) - new Date(a.createdAt)
                );

            resolve(result);
        };

        request.onerror = () => reject(request.error);
    });
}

async function markNotificationRead(notificationId) {
    const db = await openDatabase();

    return new Promise((resolve, reject) => {
        const transaction = db.transaction(
            "notifications",
            "readwrite"
        );

        const store = transaction.objectStore("notifications");
        const request = store.get(notificationId);

        request.onsuccess = () => {
            const notification = request.result;

            if (!notification) {
                reject(new Error("الإشعار غير موجود"));
                return;
            }

            notification.read = true;
            store.put(notification);
            resolve(true);
        };

        request.onerror = () => reject(request.error);
    });
}

async function getUnreadNotificationsCount(userId) {
    const notifications = await getNotifications(userId);

    return notifications.filter(item => !item.read).length;
}

async function deleteNotification(notificationId) {
    const db = await openDatabase();

    return new Promise((resolve, reject) => {
        const transaction = db.transaction(
            "notifications",
            "readwrite"
        );

        const request = transaction
            .objectStore("notifications")
            .delete(notificationId);

        request.onsuccess = () => resolve(true);
        request.onerror = () => reject(request.error);
    });
                       }
