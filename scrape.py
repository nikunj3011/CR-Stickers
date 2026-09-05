import io
import os
import requests
from PIL import Image

# Configuration
API_BASE_URL = "https://fankit.supercell.com/api/assets/search/345"
OUTPUT_DIR = "./output_emotes"
TARGET_SIZE = (512, 512)
MAX_FILE_SIZE_KB = 100
MAX_BYTES = MAX_FILE_SIZE_KB * 1024


def setup_directory():
    """Creates output folder if it doesn't exist."""
    os.makedirs(OUTPUT_DIR, exist_ok=True)


def get_image_url(asset_data):
    """
    Extracts the direct image URL from an asset payload.
    Adjusts based on standard Brandfolder / Supercell API response structure.
    """
    # Try looking for download URL or preview links
    if "url" in asset_data:
        return asset_data["url"]
    elif "generic_url" in asset_data:
        return asset_data["generic_url"]
    elif "thumbnail" in asset_data:
        return asset_data["thumbnail"]
    elif "included" in asset_data and len(asset_data["included"]) > 0:
        return asset_data["included"][0].get("url")

    # Fallback search inside 'files' array if present
    files = asset_data.get("files", [])
    if files and isinstance(files, list):
        return files[0].get("url")

    return None


def convert_and_compress_webp(img_bytes, output_path):
    """
    Resizes an image to 512x512, converts to WebP, and dynamically 
    adjusts quality to ensure it remains under 100KB.
    """
    try:
        image = Image.open(io.BytesIO(img_bytes)).convert("RGBA")

        # Resize image using high-quality Lanczos resampling
        image = image.resize(TARGET_SIZE, Image.Resampling.LANCZOS)

        # Iteratively reduce quality if file exceeds 100 KB
        quality = 90
        step = 5

        while quality >= 10:
            buffer = io.BytesIO()
            # Save to memory buffer first to inspect file size
            image.save(buffer, format="WEBP", quality=quality, method=6)
            size_in_bytes = buffer.tell()

            if size_in_bytes <= MAX_BYTES or quality <= 10:
                # Write to disk once size requirement is met
                with open(output_path, "wb") as f:
                    f.write(buffer.getvalue())

                size_kb = size_in_bytes / 1024
                print(f"  Saved: {os.path.basename(output_path)} ({size_kb:.2f} KB | Q: {quality})")
                break

            quality -= step

    except Exception as e:
        print(f"  [Error processing image]: {e}")


def fetch_and_process_all_emotes():
    setup_directory()

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    current_page = 1
    limit = 25
    total_processed = 0

    print("Starting API download & conversion pipeline...\n")

    while True:
        params = {
            "limit": limit,
            "page": current_page,
            "order": "NEWEST",
            "asset-type16": "Emotes",
        }

        print(f"Fetching API page {current_page}...")
        response = requests.get(API_BASE_URL, params=params, headers=headers)

        if response.status_code != 200:
            print(f"Failed to fetch page {current_page}. Status code: {response.status_code}")
            break

        data = response.json()

        # Parse items array (checking common key structures like 'data', 'items', 'assets')
        items = data.get("data") or data.get("items") or data.get("assets") or []

        if not items and isinstance(data, list):
            items = data

        if not items:
            print("No more items found. Process finished!")
            break

        for idx, item in enumerate(items, start=1):
            asset_id = item.get("id", f"emote_p{current_page}_{idx}")
            
            # Get name or asset_id, ensuring it is converted to string before calling .replace()
            raw_name = item.get("title") if item.get("title") is not None else asset_id
            name = str(raw_name).replace(" ", "_").replace("/", "_").replace("\\", "_")

            img_url = get_image_url(item)

            if not img_url:
                print(f"  Skipping '{name}': No image URL found.")
                continue

            print(f"Processing [{total_processed + 1}]: {name}")

            # Download raw image bytes
            img_res = requests.get(img_url, headers=headers)
            if img_res.status_code == 200:
                output_filename = f"{name}.webp"
                output_file_path = os.path.join(OUTPUT_DIR, output_filename)

                convert_and_compress_webp(img_res.content, output_file_path)
                total_processed += 1
            else:
                print(f"  Failed to download image from {img_url}")

        # Check if there's a next page or if we reached the last page
        total_pages = data.get("pagination", {}).get("total_pages") or data.get("totalPages")
        
        if total_pages and current_page >= total_pages:
            print("\nReached the last page.")
            break

        current_page += 1

    print(f"\nCompleted! Processed {total_processed} emotes.")


if __name__ == "__main__":
    fetch_and_process_all_emotes()