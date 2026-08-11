"""Serving paketi.

Not: `geolens.serving.app` alt modülü bilerek burada import edilmez — aksi halde
`geolens.serving.app` adı package attribute'u (FastAPI instance) tarafından
gölgelenir ve testlerde module import'u kırılır.

`uvicorn geolens.serving.app:app` doğrudan submodule yolunu kullanır.
"""
