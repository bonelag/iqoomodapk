"""Generate modern adaptive and raster launcher icons for Vivo Game Unlock."""
import math
import os
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'

def create_master_icon(size=512, round_mask=False):
    # High resolution canvas for super-sampling
    scale = 4
    dim = size * scale
    img = Image.new('RGBA', (dim, dim), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    cx, cy = dim / 2, dim / 2
    
    # Outer radius
    if round_mask:
        # Circular icon
        r = dim * 0.46
        # Background gradient circle
        bg_color_1 = (13, 27, 24, 255)
        bg_color_2 = (22, 46, 40, 255)
        # Create circular gradient
        for i in range(int(r), 0, -2):
            t = i / r
            c = (
                int(bg_color_1[0] * (1 - t) + bg_color_2[0] * t),
                int(bg_color_1[1] * (1 - t) + bg_color_2[1] * t),
                int(bg_color_1[2] * (1 - t) + bg_color_2[2] * t),
                255
            )
            draw.ellipse([cx - i, cy - i, cx + i, cy + i], fill=c)
        # Outer neon border
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(0, 245, 184, 180), width=int(3 * scale))
    else:
        # Rounded squircle / rounded rect for standard launcher
        corner_r = dim * 0.22
        pad = dim * 0.04
        # Draw rounded rectangle background
        draw.rounded_rectangle([pad, pad, dim - pad, dim - pad], radius=corner_r, fill=(16, 35, 30, 255))
        # Inner gradient glow
        inner_pad = pad + dim * 0.02
        draw.rounded_rectangle([inner_pad, inner_pad, dim - inner_pad, dim - inner_pad], radius=corner_r * 0.9, fill=(22, 48, 41, 255))
        # Subtle border
        draw.rounded_rectangle([pad, pad, dim - pad, dim - pad], radius=corner_r, outline=(0, 245, 184, 140), width=int(2.5 * scale))

    # Ambient tech ring
    ring_r = dim * 0.35
    draw.ellipse([cx - ring_r, cy - ring_r, cx + ring_r, cy + ring_r], outline=(0, 245, 184, 40), width=int(2 * scale))
    
    # Speed/boost arcs (indicating FPS boost / 120Hz)
    arc_r = dim * 0.38
    draw.arc([cx - arc_r, cy - arc_r, cx + arc_r, cy + arc_r], start=-45, end=45, fill=(0, 245, 184, 180), width=int(3 * scale))
    draw.arc([cx - arc_r, cy - arc_r, cx + arc_r, cy + arc_r], start=135, end=225, fill=(0, 245, 184, 180), width=int(3 * scale))

    # Gamepad body
    # Coordinates normalized around (cx, cy)
    # Controller width = 0.58 * dim, height = 0.34 * dim
    cw = dim * 0.28
    ch = dim * 0.17
    
    # Bumpers (left and right triggers at top)
    bw = dim * 0.08
    bh = dim * 0.035
    by = cy - ch - bh * 0.6
    # Left bumper
    draw.rounded_rectangle([cx - cw * 0.75, by, cx - cw * 0.25, by + bh * 1.5], radius=bh * 0.6, fill=(40, 80, 70, 255), outline=(0, 245, 184, 180), width=int(1.5 * scale))
    # Right bumper
    draw.rounded_rectangle([cx + cw * 0.25, by, cx + cw * 0.75, by + bh * 1.5], radius=bh * 0.6, fill=(40, 80, 70, 255), outline=(0, 245, 184, 180), width=int(1.5 * scale))

    # Main gamepad body: polygonal approximation with rounded grips
    # Left grip center, Right grip center
    lg_x, rg_x = cx - cw * 0.55, cx + cw * 0.55
    grip_y = cy + ch * 0.4
    grip_r = dim * 0.14
    
    # Draw left grip and right grip circles
    draw.ellipse([lg_x - grip_r, grip_y - grip_r, lg_x + grip_r, grip_y + grip_r], fill=(240, 248, 245, 255))
    draw.ellipse([rg_x - grip_r, grip_y - grip_r, rg_x + grip_r, grip_y + grip_r], fill=(240, 248, 245, 255))
    
    # Upper body connecting the grips
    top_y = cy - ch
    draw.rounded_rectangle([cx - cw * 0.85, top_y, cx + cw * 0.85, cy + ch * 0.4], radius=dim * 0.08, fill=(240, 248, 245, 255))
    
    # Inner grip cutout at the bottom
    cut_w = dim * 0.14
    cut_top = cy + ch * 0.1
    draw.ellipse([cx - cut_w, cut_top, cx + cut_w, cut_top + cut_w * 2.2], fill=bg_color_2 if round_mask else (22, 48, 41, 255))

    # D-pad on Left Grip (Emerald teal cross)
    dpad_cx, dpad_cy = lg_x - dim * 0.02, cy - ch * 0.05
    dpad_arm = dim * 0.055
    dpad_w = dim * 0.022
    # Vertical arm
    draw.rounded_rectangle([dpad_cx - dpad_w, dpad_cy - dpad_arm, dpad_cx + dpad_w, dpad_cy + dpad_arm], radius=dpad_w * 0.4, fill=(18, 120, 95, 255))
    # Horizontal arm
    draw.rounded_rectangle([dpad_cx - dpad_arm, dpad_cy - dpad_w, dpad_cx + dpad_arm, dpad_cy + dpad_w], radius=dpad_w * 0.4, fill=(18, 120, 95, 255))
    # Center dot
    draw.ellipse([dpad_cx - dpad_w * 0.5, dpad_cy - dpad_w * 0.5, dpad_cx + dpad_w * 0.5, dpad_cy + dpad_w * 0.5], fill=(0, 245, 184, 255))

    # Action Buttons on Right Grip (XYAB 4 diamonds)
    btn_cx, btn_cy = rg_x + dim * 0.02, cy - ch * 0.05
    btn_dist = dim * 0.038
    btn_r = dim * 0.016
    # Colors: Y (Yellow), B (Red/Pink), A (Green), X (Cyan/Blue)
    draw.ellipse([btn_cx, btn_cy - btn_dist - btn_r, btn_cx + btn_r*2, btn_cy - btn_dist + btn_r], fill=(245, 158, 11, 255)) # Top
    draw.ellipse([btn_cx + btn_dist - btn_r, btn_cy - btn_r, btn_cx + btn_dist + btn_r, btn_cy + btn_r], fill=(239, 68, 68, 255)) # Right
    draw.ellipse([btn_cx, btn_cy + btn_dist - btn_r, btn_cx + btn_r*2, btn_cy + btn_dist + btn_r], fill=(16, 185, 129, 255)) # Bottom
    draw.ellipse([btn_cx - btn_dist - btn_r, btn_cy - btn_r, btn_cx - btn_dist + btn_r, btn_cy + btn_r], fill=(6, 182, 212, 255)) # Left

    # Center Turbo / Unlock Lightning Bolt Emblem
    # Central shield badge behind bolt
    badge_w = dim * 0.08
    badge_h = dim * 0.10
    draw.rounded_rectangle([cx - badge_w, cy - ch * 0.2 - badge_h * 0.5, cx + badge_w, cy - ch * 0.2 + badge_h * 0.7], radius=badge_w * 0.5, fill=(16, 35, 30, 255), outline=(0, 245, 184, 200), width=int(1.5 * scale))
    
    # Sharp Lightning Bolt (Turbo / Boost unlock)
    bolt_pts = [
        (cx + dim * 0.01, cy - ch * 0.55),
        (cx - dim * 0.045, cy - ch * 0.15),
        (cx - dim * 0.005, cy - ch * 0.15),
        (cx - dim * 0.025, cy + ch * 0.35),
        (cx + dim * 0.045, cy - ch * 0.05),
        (cx + dim * 0.005, cy - ch * 0.05),
    ]
    draw.polygon(bolt_pts, fill=(0, 245, 184, 255))
    
    # Downsample with Lanczos for super-crisp antialiasing
    return img.resize((size, size), Image.Resampling.LANCZOS)

def main():
    print("Generating icons...")
    # Res buckets:
    densities = {
        'mipmap-mdpi': 48,
        'mipmap-hdpi': 72,
        'mipmap-xhdpi': 96,
        'mipmap-xxhdpi': 144,
        'mipmap-xxxhdpi': 192,
    }
    
    for folder, size in densities.items():
        out_dir = RES / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        # Standard icon
        icon = create_master_icon(size, round_mask=False)
        icon.save(out_dir / 'ic_launcher.png', 'PNG')
        # Round icon
        round_icon = create_master_icon(size, round_mask=True)
        round_icon.save(out_dir / 'ic_launcher_round.png', 'PNG')
        print(f"Generated {folder}: {size}x{size}")

    # Generate master 512x512 preview
    dist_dir = ROOT / 'dist'
    dist_dir.mkdir(exist_ok=True)
    master = create_master_icon(512, round_mask=False)
    master.save(dist_dir / 'app_icon_preview.png', 'PNG')
    print("Generated master preview: dist/app_icon_preview.png")

if __name__ == '__main__':
    main()
