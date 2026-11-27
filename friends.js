// CONNECTO - Friends System

async function sendFriendRequest(senderId, receiverId) {
    if (!senderId || !receiverId || senderId === receiverId) {
        throw new Error("بيانات طلب الصداقة غير صحيحة");
    }

    const db = await openDatabase();

    return new Promise((resolve, reject) => {
        const transaction = db.transaction("friends", "readwrite");
        const store = transaction.objectStore("friends");

        const request = store.getAll();

        request.onsuccess = () => {
            const exists = request.result.some(item =>
                (item.senderId === senderId &&
                 item.receiverId === receiverId) ||
                (item.senderId === receiverId &&
                 item.receiverId === senderId)
            );

            if (exists) {
                reject(new Error("يوجد طلب صداقة بالفعل"));
                return;
            }

            const addRequest = store.add({
                senderId,
                receiverId,
                status: "pending",
                createdAt: new Date().toISOString()
            });

            addRequest.onsuccess = () => resolve(true);
            addRequest.onerror = () => reject(addRequest.error);
        };

        request.onerror = () => reject(request.error);
    });
}

async function getFriendRequests(userId) {
    const db = await openDatabase();

    return new Promise((resolve, reject) => {
        const transaction = db.transaction("friends", "readonly");
        const request = transaction.objectStore("friends").getAll();

        request.onsuccess = () => {
            resolve(request.result.filter(item =>
                item.receiverId === userId &&
                item.status === "pending"
            ));
        };

        request.onerror = () => reject(request.error);
    });
}

async function respondToFriendRequest(requestId, accepted) {
    const db = await openDatabase();

    return new Promise((resolve, reject) => {
        const transaction = db.transaction("friends", "readwrite");
        const store = transaction.objectStore("friends");
        const request = store.get(requestId);

        request.onsuccess = () => {
            const item = request.result;

            if (!item || item.status !== "pending") {
                reject(new Error("طلب الصداقة غير موجود"));
                return;
            }

            item.status = accepted ? "accepted" : "rejected";
            store.put(item);

            resolve(item);
        };

        request.onerror = () => reject(request.error);
    });
}

async function getFriends(userId) {
    const db = await openDatabase();

    return new Promise((resolve, reject) => {
        const transaction = db.transaction("friends", "readonly");
        const request = transaction.objectStore("friends").getAll();

        request.onsuccess = () => {
            const friends = request.result
                .filter(item =>
                    item.status === "accepted" &&
                    (item.senderId === userId ||
                     item.receiverId === userId)
                )
                .map(item =>
                    item.senderId === userId
                        ? item.receiverId
                        : item.senderId
                );

            resolve(friends);
        };

        request.onerror = () => reject(request.error);
    });
}
