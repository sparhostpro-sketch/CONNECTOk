
// CONNECTO - Upload System

const MAX_IMAGE_SIZE = 5 * 1024 * 1024;
const MAX_VIDEO_SIZE = 50 * 1024 * 1024;

function validateUpload(file) {
    if (!file) {
        throw new Error("من فضلك اختر ملفاً");
    }

    const isImage = file.type.startsWith("image/");
    const isVideo = file.type.startsWith("video/");

    if (!isImage && !isVideo) {
        throw new Error("نوع الملف غير مدعوم");
    }

    if (isImage && file.size > MAX_IMAGE_SIZE) {
        throw new Error("حجم الصورة يجب ألا يتجاوز 5 ميجابايت");
    }

    if (isVideo && file.size > MAX_VIDEO_SIZE) {
        throw new Error("حجم الفيديو يجب ألا يتجاوز 50 ميجابايت");
    }

    return true;
}

function previewUpload(file, imageElement) {
    validateUpload(file);

    const url = URL.createObjectURL(file);

    imageElement.src = url;

    return url;
}

function readFileAsDataURL(file) {
    validateUpload(file);

    return new Promise((resolve, reject) => {
        const reader = new FileReader();

        reader.onload = () => resolve(reader.result);
        reader.onerror = () => reject(reader.error);

        reader.readAsDataURL(file);
    });
}

function releasePreview(url) {
    if (url) {
        URL.revokeObjectURL(url);
    }
}
