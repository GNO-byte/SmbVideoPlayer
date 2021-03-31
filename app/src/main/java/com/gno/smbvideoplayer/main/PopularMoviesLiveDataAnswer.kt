package com.gno.smbvideoplayer.main

import jcifs.smb.SmbFile

data class PopularMoviesLiveDataAnswer(
    val listFiles: List<SmbFile>?,
    val parent: SmbFile?
)

