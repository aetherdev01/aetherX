ZIP="$(ls -t /storage/emulated/0/Download/San_Andreas_Streetlife_v5.0.3_600kmh_bikes_64bit*.zip 2>/dev/null | head -n1)"
[ -n "$ZIP" ] || { echo "Zip tidak ditemukan di Download"; exit 1; }
echo "Pakai: $ZIP"
unzip -o "$ZIP" -d "$HOME/gta"
bash "$HOME/gta/San_Andreas_Streetlife_v5.0.3/apply_v5.0.3_to_gta.sh" "$HOME/gta"
[ -f "$HOME/gta/build_termux.sh.bak" ] || cp -f "$HOME/gta/build_termux.sh" "$HOME/gta/build_termux.sh.bak"
cp -f "$HOME/gta/San_Andreas_Streetlife_v5.0.3/build_termux.sh" "$HOME/gta/build_termux.sh"
cd "$HOME/gta" || exit 1
grep -q APP_BUILD_SCRIPT build_termux.sh && grep -q g_deleteCarCommand src/state_config.h || { echo "Masih zip lama, download ulang"; exit 1; }
rm -rf obj libs
bash build_termux.sh