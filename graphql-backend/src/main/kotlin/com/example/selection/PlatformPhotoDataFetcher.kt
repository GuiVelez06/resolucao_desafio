package com.example.selection

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument

@DgsComponent
class PlatformPhotoDataFetcher(private val service: PlatformPhotoService) 
{

    @DgsQuery
    fun platformPhotos(
        @InputArgument platformId: Int,
        @InputArgument offset: Int,
        @InputArgument limit: Int,
    ): PlatformPhotoPage = service.photos(platformId.toLong(), offset, limit)
}
