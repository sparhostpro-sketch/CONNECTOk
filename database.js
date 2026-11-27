// CONNECTO - Database System

const DB_NAME = "CONNECTO_DB";
const DB_VERSION = 2;

let dbInstance = null;

function openDatabase() {
    return new Promise((resolve, reject) => {
        if (dbInstance) {
            resolve(dbInstance);
            return;
        }

        const request = indexedDB.open(DB_NAME, DB_VERSION);

        request.onupgradeneeded = function(event) {
            const db = event.target.result;

            const stores = [
                "users",
                "posts",
                "messages",
                "friends",
                "notifications"
            ];

            stores.forEach(name => {
                if (!db.objectStoreNames.contains(name)) {
                    db.createObjectStore(name, {
                        keyPath: "id",
                        autoIncrement: true
                    });
                }
            });
        };

        request.onsuccess = function(event) {
            dbInstance = event.target.result;

            dbInstance.onversionchange = function() {
                dbInstance.close();
                dbInstance = null;
            };

            resolve(dbInstance);
        };

        request.onerror = function() {
            reject(request.error);
        };

        request.onblocked = function() {
            console.warn("Database upgrade is blocked.");
        };
    });
}

async function saveData(storeName, data) {
    const db = await openDatabase();

    return new Promise((resolve, reject) => {
        if (!db.objectStoreNames.contains(storeName)) {
            reject(new Error("Database store not found: " + storeName));
            return;
        }

        const transaction = db.transaction(storeName, "readwrite");
        const store = transaction.objectStore(storeName);
        const request = store.add(data);

        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error);
    });
}

async function getAllData(storeName) {
    const db = await openDatabase();

    return new Promise((resolve, reject) => {
        if (!db.objectStoreNames.contains(storeName)) {
            reject(new Error("Database store not found: " + storeName));
            return;
        }

        const transaction = db.transaction(storeName, "readonly");
        const request = transaction.objectStore(storeName).getAll();

        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error);
    });
}

async function getData(storeName, id) {
    const db = await openDatabase();

    return new Promise((resolve, reject) => {
        const transaction = db.transaction(storeName, "readonly");
        const request = transaction.objectStore(storeName).get(id);

        request.onsuccess = () => resolve(request.result || null);
        request.onerror = () => reject(request.error);
    });
}

async function updateData(storeName, data) {
    const db = await openDatabase();

    return new Promise((resolve, reject) => {
        const transaction = db.transaction(storeName, "readwrite");
        const request = transaction.objectStore(storeName).put(data);

        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error);
    });
}

async function deleteData(storeName, id) {
    const db = await openDatabase();

    return new Promise((resolve, reject) => {
        const transaction = db.transaction(storeName, "readwrite");
        const request = transaction.objectStore(storeName).delete(id);

        request.onsuccess = () => resolve(true);
        request.onerror = () => reject(request.error);
    });
}
