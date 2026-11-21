from PIL import Image

im = Image.open('C:/Users/David/.gemini/antigravity/brain/4f299545-c76e-4f8e-8079-349d62bd4eba/scratch/master_logo_crop.png').convert('RGB')
w, h = im.size

out = Image.new('RGBA', (w, h), (0, 0, 0, 0))

B_BLACK = 24
B_SOLID = 68

for y in range(h):
    for x in range(w):
        # Precise boundary exclusions for lens flares
        if (x < 210 and y < 160) or (x + y < 235):
            continue
        if (x > 635 and y < 160):
            continue
        if y > 551: # strictly below the bottom line
            continue
        if x > 778 and y > 515: # right of the bottom line
            continue
        if x < 68 and y > 515: # left of the bottom line
            continue

        r, g, b = im.getpixel((x, y))
        max_c = max(r, g, b)

        if max_c <= B_BLACK:
            continue
        elif max_c >= B_SOLID:
            out.putpixel((x, y), (r, g, b, 255))
        else:
            t = (max_c - B_BLACK) / (B_SOLID - B_BLACK)
            alpha_f = t * t * (3 - 2 * t)
            alpha = int(255 * alpha_f)
            if alpha < 2:
                continue
            # Unmultiply black out: divide by alpha_f to restore clean foreground color
            unmul = min(255.0, 255.0 * (max_c / 255.0) / max(alpha_f, 0.18))
            scale = unmul / max_c
            nr = min(255, int(r * scale))
            ng = min(255, int(g * scale))
            nb = min(255, int(b * scale))
            out.putpixel((x, y), (nr, ng, nb, alpha))

bbox = out.getbbox()
print('Bounding box:', bbox)
cropped = out.crop(bbox)

# Add 8px transparent padding all around
final_w, final_h = cropped.width + 16, cropped.height + 16
brand_header = Image.new('RGBA', (final_w, final_h), (0, 0, 0, 0))
brand_header.paste(cropped, (8, 8))

# Save brand_header.png to destination
brand_header.save('app/src/main/res/drawable-nodpi/brand_header.png', 'PNG')
print('Saved app/src/main/res/drawable-nodpi/brand_header.png size:', brand_header.size)

# Composite on sidebar dark navy background for verification
bg_test = Image.new('RGBA', (final_w + 60, final_h + 60), (20, 24, 41, 255))
bg_test.paste(brand_header, (30, 30), brand_header)
bg_test.save('C:/Users/David/.gemini/antigravity/brain/4f299545-c76e-4f8e-8079-349d62bd4eba/scratch/brand_header_test.png')

# Also generate app_logo.png (emblem only, transparent)
# The emblem is from y=0 to y=380 roughly
emblem_crop = brand_header.crop((160, 0, 680, 380))
emblem_bbox = emblem_crop.getbbox()
emblem_clean = emblem_crop.crop(emblem_bbox)
# Pad to square 512x512
app_logo = Image.new('RGBA', (512, 512), (0, 0, 0, 0))
# Scale emblem to fit ~440px
ratio = min(440.0 / emblem_clean.width, 440.0 / emblem_clean.height)
new_w, new_h = int(emblem_clean.width * ratio), int(emblem_clean.height * ratio)
emblem_resized = emblem_clean.resize((new_w, new_h), Image.Resampling.LANCZOS)
app_logo.paste(emblem_resized, ((512 - new_w) // 2, (512 - new_h) // 2))
app_logo.save('app/src/main/res/drawable-nodpi/app_logo.png', 'PNG')
print('Saved app/src/main/res/drawable-nodpi/app_logo.png size:', app_logo.size)
