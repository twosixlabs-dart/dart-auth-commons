package com.twosixlabs.dart.auth.tenant

sealed trait DartTenant {
    def isChildOf( that : DartTenant ) : Boolean
    def isParentOf( that : DartTenant ) : Boolean
}

case object GlobalCorpus extends DartTenant {
    override def isChildOf( that : DartTenant ) : Boolean = false
    override def isParentOf( that : DartTenant ) : Boolean = that match {
        case GlobalCorpus => false
        case CorpusTenant( _, _ ) => true
    }
}

case class CorpusTenant( id : String, parent : DartTenant = GlobalCorpus ) extends DartTenant {
    override def isChildOf( that : DartTenant ) : Boolean = {
        that == GlobalCorpus || parent == that || parent.isChildOf( that )
    }

    override def isParentOf( that : DartTenant ) : Boolean = that match {
        case GlobalCorpus => false
        case CorpusTenant( _, thatParent ) => this == thatParent || isParentOf( thatParent )
    }

    require( id != DartTenant.globalId )
}

object DartTenant {
    val globalId : String = "global"

    def fromString( str : String ) : DartTenant = str match {
        case `globalId` => GlobalCorpus
        case other => CorpusTenant( other, GlobalCorpus )
    }
}
